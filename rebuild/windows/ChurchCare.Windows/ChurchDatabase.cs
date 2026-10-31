using Microsoft.Data.Sqlite;

namespace ChurchCare.Windows;

public sealed class ChurchDatabase
{
    private readonly string _connectionString;

    public ChurchDatabase()
    {
        var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ChurchCare");
        Directory.CreateDirectory(folder);
        var path = Path.Combine(folder, "churchcare-windows.db");
        _connectionString = new SqliteConnectionStringBuilder { DataSource = path, ForeignKeys = true }.ToString();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS church_profile (
                id INTEGER PRIMARY KEY CHECK(id=1),
                church_name TEXT NOT NULL,
                starting_unit_name TEXT NOT NULL,
                starting_level TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS structure_levels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                level_name TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                UNIQUE(level_name COLLATE NOCASE)
            );
            CREATE TABLE IF NOT EXISTS church_units (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                unit_name TEXT NOT NULL,
                level_name TEXT NOT NULL,
                parent_unit_id INTEGER NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(parent_unit_id) REFERENCES church_units(id)
            );
            CREATE TABLE IF NOT EXISTS members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                membership_status TEXT NOT NULL DEFAULT 'Active',
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS appointments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                appointment_at TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT ''
            );
            CREATE TABLE IF NOT EXISTS care_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER NULL,
                category TEXT NOT NULL,
                notes TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(member_id) REFERENCES members(id)
            );
            CREATE TABLE IF NOT EXISTS delegations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                assignee TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'Pending'
            );
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """;
        command.ExecuteNonQuery();
    }

    private SqliteConnection Open()
    {
        var connection = new SqliteConnection(_connectionString);
        connection.Open();
        return connection;
    }

    public bool HasProfile()
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "SELECT COUNT(*) FROM church_profile WHERE id=1";
        return Convert.ToInt32(q.ExecuteScalar()) > 0;
    }

    public void SaveSetup(string church, string unit, string startingLevel, IReadOnlyList<string> levels)
    {
        using var c = Open();
        using var tx = c.BeginTransaction();
        using (var q = c.CreateCommand())
        {
            q.Transaction = tx;
            q.CommandText = "INSERT OR REPLACE INTO church_profile(id,church_name,starting_unit_name,starting_level,created_at) VALUES(1,$church,$unit,$level,$created)";
            q.Parameters.AddWithValue("$church", church);
            q.Parameters.AddWithValue("$unit", unit);
            q.Parameters.AddWithValue("$level", startingLevel);
            q.Parameters.AddWithValue("$created", DateTimeOffset.UtcNow.ToString("O"));
            q.ExecuteNonQuery();
        }
        using (var q = c.CreateCommand())
        {
            q.Transaction = tx;
            q.CommandText = "DELETE FROM structure_levels";
            q.ExecuteNonQuery();
        }
        for (var i = 0; i < levels.Count; i++)
        {
            using var q = c.CreateCommand();
            q.Transaction = tx;
            q.CommandText = "INSERT INTO structure_levels(level_name,sort_order) VALUES($name,$order)";
            q.Parameters.AddWithValue("$name", levels[i]);
            q.Parameters.AddWithValue("$order", i);
            q.ExecuteNonQuery();
        }
        using (var q = c.CreateCommand())
        {
            q.Transaction = tx;
            q.CommandText = "INSERT INTO church_units(unit_name,level_name,created_at) VALUES($unit,$level,$created)";
            q.Parameters.AddWithValue("$unit", unit);
            q.Parameters.AddWithValue("$level", startingLevel);
            q.Parameters.AddWithValue("$created", DateTimeOffset.UtcNow.ToString("O"));
            q.ExecuteNonQuery();
        }
        tx.Commit();
        Log("Church profile created");
    }

    public (string Church, string Unit, string Level) GetProfile()
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "SELECT church_name,starting_unit_name,starting_level FROM church_profile WHERE id=1";
        using var r = q.ExecuteReader();
        return r.Read() ? (r.GetString(0), r.GetString(1), r.GetString(2)) : ("ChurchCare", "", "");
    }

    public List<string> GetLevels()
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "SELECT level_name FROM structure_levels ORDER BY sort_order,id";
        using var r = q.ExecuteReader();
        var rows = new List<string>();
        while (r.Read()) rows.Add(r.GetString(0));
        return rows;
    }

    public void AddLevel(string name)
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "INSERT INTO structure_levels(level_name,sort_order) VALUES($name,(SELECT COALESCE(MAX(sort_order),-1)+1 FROM structure_levels))";
        q.Parameters.AddWithValue("$name", name);
        q.ExecuteNonQuery();
        Log("Added structure level: " + name);
    }

    public void AddMember(string name, string phone, string email)
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "INSERT INTO members(full_name,phone,email,created_at) VALUES($name,$phone,$email,$created)";
        q.Parameters.AddWithValue("$name", name);
        q.Parameters.AddWithValue("$phone", phone);
        q.Parameters.AddWithValue("$email", email);
        q.Parameters.AddWithValue("$created", DateTimeOffset.UtcNow.ToString("O"));
        q.ExecuteNonQuery();
        Log("Added member: " + name);
    }

    public List<(long Id, string Name, string Phone, string Email, string Status)> GetMembers(string search = "")
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "SELECT id,full_name,phone,email,membership_status FROM members WHERE full_name LIKE $search OR phone LIKE $search ORDER BY full_name";
        q.Parameters.AddWithValue("$search", "%" + search + "%");
        using var r = q.ExecuteReader();
        var rows = new List<(long,string,string,string,string)>();
        while (r.Read()) rows.Add((r.GetInt64(0),r.GetString(1),r.GetString(2),r.GetString(3),r.GetString(4)));
        return rows;
    }

    public int Count(string table)
    {
        var allowed = new HashSet<string> { "members", "appointments", "care_records", "delegations", "church_units" };
        if (!allowed.Contains(table)) return 0;
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "SELECT COUNT(*) FROM " + table;
        return Convert.ToInt32(q.ExecuteScalar());
    }

    public string DatabasePath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ChurchCare", "churchcare-windows.db");

    private void Log(string action)
    {
        using var c = Open();
        using var q = c.CreateCommand();
        q.CommandText = "INSERT INTO audit_log(action,created_at) VALUES($action,$created)";
        q.Parameters.AddWithValue("$action", action);
        q.Parameters.AddWithValue("$created", DateTimeOffset.UtcNow.ToString("O"));
        q.ExecuteNonQuery();
    }
}
