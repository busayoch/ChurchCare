#!/usr/bin/env python3
"""Restricted ChurchCare Firebase role administration via GitHub Actions.

This tool is intentionally not callable from the Android client. It uses a
GitHub Actions secret containing a narrowly permissioned Firebase service
account. The repository-owner-only workflow is the human authorization gate.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Any

import firebase_admin
from firebase_admin import auth, credentials, firestore


PROJECT_ID = "churchcare-cloud-2026"
ROLE_FIELD = "churchcareRole"


def fail(message: str) -> None:
    raise SystemExit(message)


def verified_user(uid: str):
    if not uid or len(uid) > 128:
        fail("A valid Firebase UID is required.")
    try:
        user = auth.get_user(uid)
    except auth.UserNotFoundError:
        fail("The supplied Firebase UID does not exist.")
    if not user.email or not user.email_verified:
        fail("The target account must have a verified email address.")
    return user


def initialize():
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if not raw:
        fail("Missing GitHub secret FIREBASE_SERVICE_ACCOUNT_JSON.")
    try:
        service_account = json.loads(raw)
    except json.JSONDecodeError:
        fail("FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON.")
    if service_account.get("project_id") != PROJECT_ID:
        fail("The service-account key belongs to a different Firebase project.")
    cred = credentials.Certificate(service_account)
    app = firebase_admin.initialize_app(cred, {"projectId": PROJECT_ID})
    return firestore.client(app=app)


def write_claims(uid: str, role: str | None) -> None:
    user = auth.get_user(uid)
    claims: dict[str, Any] = dict(user.custom_claims or {})
    if role is None:
        claims.pop(ROLE_FIELD, None)
    else:
        claims[ROLE_FIELD] = role
    # Preserve unrelated custom claims owned by other trusted systems.
    auth.set_custom_user_claims(uid, claims or None)


def require_owner(requester_uid: str, db) -> None:
    requester = verified_user(requester_uid)
    claims = requester.custom_claims or {}
    if claims.get(ROLE_FIELD) != "owner":
        fail("The requesting Firebase account does not hold the ChurchCare Owner claim.")
    admin_ref = db.collection("system").document("admin")
    admin_doc = admin_ref.get()
    if not admin_doc.exists or admin_doc.get("ownerUid") != requester_uid:
        fail("The requesting UID does not match the registered ChurchCare Owner.")


def bootstrap_owner(target_uid: str, db) -> None:
    target = verified_user(target_uid)
    admin_ref = db.collection("system").document("admin")

    @firestore.transactional
    def reserve(transaction):
        snapshot = admin_ref.get(transaction=transaction)
        if snapshot.exists:
            registered_uid = snapshot.get("ownerUid")
            if registered_uid and registered_uid != target_uid:
                fail("An initial Owner is already registered. Bootstrap cannot replace that account.")
            if not registered_uid:
                transaction.set(admin_ref, {
                    "ownerUid": target_uid,
                    "bootstrapStatus": "pending",
                }, merge=True)
        else:
            transaction.set(admin_ref, {
                "ownerUid": target_uid,
                "bootstrapStatus": "pending",
            })

    reserve(db.transaction())

    # If the process stops here, rerunning for the same UID safely completes
    # the operation; a different UID cannot take over the reserved bootstrap.
    write_claims(target_uid, "owner")
    db.collection("system").document("roles").collection("users").document(target_uid).set({
        "uid": target_uid,
        "role": "owner",
        "status": "active",
        "email": target.email,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }, merge=True)
    admin_ref.set({
        "ownerUid": target_uid,
        "bootstrapStatus": "complete",
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }, merge=True)
    print("ChurchCare initial Owner successfully registered.")
    print("The user must refresh their Firebase ID token (or sign in again) to receive the new claim.")


def grant_reviewer(requester_uid: str, target_uid: str, db) -> None:
    require_owner(requester_uid, db)
    target = verified_user(target_uid)
    admin_ref = db.collection("system").document("admin")
    if target_uid == admin_ref.get().get("ownerUid"):
        fail("The Owner already has all reviewer authority; the Owner cannot be downgraded.")
    existing = target.custom_claims or {}
    if existing.get(ROLE_FIELD) == "owner":
        fail("Refusing to change an account that already carries an Owner claim.")
    write_claims(target_uid, "reviewer")
    db.collection("system").document("roles").collection("users").document(target_uid).set({
        "uid": target_uid,
        "role": "reviewer",
        "status": "active",
        "email": target.email,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }, merge=True)
    print("Reviewer role granted. The target user must refresh their Firebase ID token.")


def revoke_reviewer(requester_uid: str, target_uid: str, db) -> None:
    require_owner(requester_uid, db)
    target = verified_user(target_uid)
    admin_ref = db.collection("system").document("admin")
    if target_uid == admin_ref.get().get("ownerUid"):
        fail("The initial Owner cannot be revoked through reviewer management.")
    existing = target.custom_claims or {}
    if existing.get(ROLE_FIELD) != "reviewer":
        fail("The target account does not currently hold the Reviewer claim.")
    write_claims(target_uid, None)
    db.collection("system").document("roles").collection("users").document(target_uid).set({
        "uid": target_uid,
        "role": "none",
        "status": "revoked",
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }, merge=True)
    print("Reviewer role revoked. The target user's existing ID token remains valid until refreshed or expired.")


def main() -> None:
    parser = argparse.ArgumentParser(description="ChurchCare trusted role administration")
    parser.add_argument("--operation", required=True,
                        choices=("bootstrap-owner", "grant-reviewer", "revoke-reviewer"))
    parser.add_argument("--target-uid", required=True)
    parser.add_argument("--requester-uid", default="")
    args = parser.parse_args()

    db = initialize()
    if args.operation == "bootstrap-owner":
        bootstrap_owner(args.target_uid, db)
        return
    if not args.requester_uid:
        fail("A requester Firebase UID is required for reviewer changes.")
    if args.operation == "grant-reviewer":
        grant_reviewer(args.requester_uid, args.target_uid, db)
    else:
        revoke_reviewer(args.requester_uid, args.target_uid, db)


if __name__ == "__main__":
    main()
