using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace ChurchCare.Windows;

public sealed partial class MainWindow : Window
{
    private readonly ChurchDatabase _database = new();
    private string _currentPage = "Dashboard";

    public MainWindow()
    {
        InitializeComponent();
        Title = "ChurchCare";
        Navigation.SelectedItem = Navigation.MenuItems[0];
        RenderPage("Dashboard");
        if (!_database.HasProfile()) _ = ShowFirstRunSetupAsync();
    }

    private void Navigation_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.IsSettingsSelected) RenderPage("Settings");
        else if (args.SelectedItemContainer?.Tag is string tag) RenderPage(tag);
    }

    private void RenderPage(string page)
    {
        _currentPage = page;
        PageHost.Children.Clear();
        var profile = _database.GetProfile();
        AddText("ChurchCare", 30, true);
        AddText(profile.Church + (string.IsNullOrWhiteSpace(profile.Unit) ? "" : "  •  " + profile.Unit), 14, false, "#667085");
        AddDivider();

        switch (page)
        {
            case "Dashboard": RenderDashboard(profile); break;
            case "Structure": RenderStructure(); break;
            case "Members": RenderMembers(); break;
            case "Appointments": RenderPlaceholder("Appointments", "Keep pastoral appointments and follow-ups organised."); break;
            case "Care": RenderPlaceholder("Care records", "Keep a careful record of visits, welfare follow-up and pastoral care."); break;
            case "Delegations": RenderPlaceholder("Delegations", "Delegation management will be connected to ChurchCare's granted-authority model."); break;
            case "Backup": RenderBackup(); break;
            case "Settings": RenderSettings(); break;
            default: RenderDashboard(profile); break;
        }
    }

    private void RenderDashboard((string Church, string Unit, string Level) profile)
    {
        AddText("Welcome to ChurchCare", 24, true);
        AddText("Your church workspace, ready for everyday administration.", 15, false, "#667085");
        var cards = new Grid { ColumnSpacing = 14, Margin = new Thickness(0, 12, 0, 12) };
        for (int i = 0; i < 4; i++) cards.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        AddMetric(cards, 0, "Members", _database.Count("members").ToString());
        AddMetric(cards, 1, "Church units", _database.Count("church_units").ToString());
        AddMetric(cards, 2, "Appointments", _database.Count("appointments").ToString());
        AddMetric(cards, 3, "Care records", _database.Count("care_records").ToString());
        PageHost.Children.Add(cards);
        AddText("Your church", 19, true);
        AddText(profile.Church, 17, true, "#1769AA");
        AddText("Starting unit: " + profile.Unit + "  |  Level: " + profile.Level, 14, false, "#667085");
        AddText("Quick actions", 19, true);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        actions.Children.Add(MakeButton("Add a member", async () => await AddMemberDialogAsync()));
        actions.Children.Add(MakeButton("Church structure", () => RenderPage("Structure")));
        actions.Children.Add(MakeButton("View members", () => RenderPage("Members")));
        PageHost.Children.Add(actions);
        AddText("This PC workspace currently stores its records locally and works offline. Cross-device sign-in and synchronisation with Android are planned as a shared-backend milestone; they are not active in this foundation build.", 13, false, "#8A5A00");
    }

    private void RenderStructure()
    {
        AddText("Church structure", 24, true);
        AddText("Your church's levels are ordered from highest to lowest. A branch can start independently and connect to its correct parent later.", 14, false, "#667085");
        foreach (var level in _database.GetLevels())
        {
            var card = new Border { Padding = new Thickness(14), Margin = new Thickness(0, 3, 0, 3), CornerRadius = new CornerRadius(8), Background = new SolidColorBrush(Color.FromArgb(255, 244, 248, 252)) };
            card.Child = new TextBlock { Text = level, FontSize = 16, Foreground = new SolidColorBrush(Color.FromArgb(255, 32, 48, 66)) };
            PageHost.Children.Add(card);
        }
        var entry = new TextBox { Header = "New organisational level", PlaceholderText = "e.g. District, Area, Assembly" };
        PageHost.Children.Add(entry);
        PageHost.Children.Add(MakeButton("Add level", () =>
        {
            var value = entry.Text.Trim();
            if (value.Length == 0) return;
            try { _database.AddLevel(value); RenderPage("Structure"); }
            catch { ShowMessageAsync("That level could not be added. Check that its name is not already in the structure."); }
        }));
    }

    private void RenderMembers()
    {
        AddText("Members", 24, true);
        var search = new TextBox { PlaceholderText = "Search by name or phone number", Width = 420 };
        PageHost.Children.Add(search);
        PageHost.Children.Add(MakeButton("Add member", async () => await AddMemberDialogAsync()));
        var list = new StackPanel { Spacing = 8 };
        void Refresh()
        {
            list.Children.Clear();
            foreach (var member in _database.GetMembers(search.Text))
            {
                var panel = new StackPanel { Spacing = 2, Padding = new Thickness(12) };
                panel.Children.Add(new TextBlock { Text = member.Name, FontSize = 17, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
                panel.Children.Add(new TextBlock { Text = string.Join("  •  ", new[] { member.Phone, member.Email, member.Status }.Where(x => !string.IsNullOrWhiteSpace(x))), FontSize = 13, Foreground = new SolidColorBrush(Color.FromArgb(255, 102, 112, 133)) });
                list.Children.Add(new Border { Child = panel, CornerRadius = new CornerRadius(8), BorderBrush = new SolidColorBrush(Color.FromArgb(255, 225, 231, 238)), BorderThickness = new Thickness(1) });
            }
            if (list.Children.Count == 0) list.Children.Add(new TextBlock { Text = "No members found. Add your first member to begin.", Margin = new Thickness(0, 12, 0, 0) });
        }
        search.TextChanged += (_, _) => Refresh();
        PageHost.Children.Add(list);
        Refresh();
    }

    private void RenderPlaceholder(string title, string description)
    {
        AddText(title, 24, true);
        AddText(description, 15, false, "#667085");
        AddText("This module is included in the desktop navigation foundation. Its full record workflows will be implemented in the next desktop milestones.", 14, false);
    }

    private void RenderBackup()
    {
        AddText("Backup & restore", 24, true);
        AddText("Your local ChurchCare database is stored on this PC.", 15, false, "#667085");
        AddText(_database.DatabasePath, 13, false);
        PageHost.Children.Add(MakeButton("Open database folder", () =>
        {
            try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("explorer.exe", "/select,\"" + _database.DatabasePath + "\"") { UseShellExecute = true }); }
            catch { ShowMessageAsync("The database folder could not be opened."); }
        }));
        AddText("A guided backup and restore workflow will be added before this desktop build is considered production-ready.", 13, false, "#8A5A00");
    }

    private void RenderSettings()
    {
        AddText("About ChurchCare", 24, true);
        AddText("ChurchCare for Windows", 17, true, "#1769AA");
        AddText("Desktop foundation • Version 0.1.0", 14, false);
        AddText("Native Windows desktop application built with C# and WinUI 3. This is not a browser app or web wrapper.", 14, false);
        AddText("Local data folder", 16, true);
        AddText(_database.DatabasePath, 13, false);
    }

    private async Task ShowFirstRunSetupAsync()
    {
        var church = new TextBox { Header = "Church name", PlaceholderText = "e.g. Grace Chapel" };
        var levels = new TextBox { Header = "Church structure — highest level first (one per line)", AcceptsReturn = true, Height = 130, Text = "National\nDistrict\nArea\nAssembly" };
        var starting = new TextBox { Header = "Starting level for this registration", Text = "Assembly" };
        var unit = new TextBox { Header = "Name of this branch or unit", PlaceholderText = "e.g. Grace Chapel, Ikeja" };
        var content = new StackPanel { Spacing = 10 };
        content.Children.Add(new TextBlock { Text = "Create your church profile", FontSize = 20, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
        content.Children.Add(new TextBlock { Text = "Define your own hierarchy. Add levels from highest to lowest, then identify the branch that is starting ChurchCare." });
        content.Children.Add(church); content.Children.Add(levels); content.Children.Add(starting); content.Children.Add(unit);
        var dialog = new ContentDialog { XamlRoot = Content.XamlRoot, Title = "Welcome to ChurchCare", Content = content, PrimaryButtonText = "Create profile", CloseButtonText = "Not now", DefaultButton = ContentDialogButton.Primary };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        var names = levels.Text.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        if (string.IsNullOrWhiteSpace(church.Text) || string.IsNullOrWhiteSpace(unit.Text) || names.Count == 0 || !names.Contains(starting.Text.Trim(), StringComparer.OrdinalIgnoreCase))
        {
            await ShowMessageAsync("Please enter the church name, at least one unique level, a starting level that appears in the list, and the branch/unit name.");
            await ShowFirstRunSetupAsync();
            return;
        }
        _database.SaveSetup(church.Text.Trim(), unit.Text.Trim(), starting.Text.Trim(), names);
        RenderPage("Dashboard");
    }

    private async Task AddMemberDialogAsync()
    {
        var name = new TextBox { Header = "Full name", PlaceholderText = "Member's full name" };
        var phone = new TextBox { Header = "Phone number", PlaceholderText = "Phone number" };
        var email = new TextBox { Header = "Email address", PlaceholderText = "Optional" };
        var panel = new StackPanel { Spacing = 10 };
        panel.Children.Add(name); panel.Children.Add(phone); panel.Children.Add(email);
        var dialog = new ContentDialog { XamlRoot = Content.XamlRoot, Title = "Add a member", Content = panel, PrimaryButtonText = "Save member", CloseButtonText = "Cancel", DefaultButton = ContentDialogButton.Primary };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        if (string.IsNullOrWhiteSpace(name.Text)) { await ShowMessageAsync("Enter the member's name."); return; }
        _database.AddMember(name.Text.Trim(), phone.Text.Trim(), email.Text.Trim());
        RenderPage(_currentPage == "Dashboard" ? "Dashboard" : "Members");
    }

    private async Task ShowMessageAsync(string message)
    {
        var dialog = new ContentDialog { XamlRoot = Content.XamlRoot, Title = "ChurchCare", Content = message, CloseButtonText = "OK" };
        await dialog.ShowAsync();
    }

    private void AddText(string text, double size, bool bold, string? color = null)
    {
        var block = new TextBlock { Text = text, FontSize = size, TextWrapping = Microsoft.UI.Xaml.TextWrapping.Wrap, FontWeight = bold ? Microsoft.UI.Text.FontWeights.SemiBold : Microsoft.UI.Text.FontWeights.Normal };
        if (color is not null) block.Foreground = new SolidColorBrush(ParseColor(color));
        PageHost.Children.Add(block);
    }

    private void AddDivider() => PageHost.Children.Add(new Border { Height = 1, Background = new SolidColorBrush(Color.FromArgb(255, 225, 231, 238)), Margin = new Thickness(0, 8, 0, 8) });

    private Button MakeButton(string text, Action action)
    {
        var button = new Button { Content = text, Padding = new Thickness(18, 10, 18, 10), Margin = new Thickness(0, 4, 0, 4) };
        button.Click += (_, _) => action();
        return button;
    }

    private void AddMetric(Grid grid, int column, string label, string value)
    {
        var stack = new StackPanel { Spacing = 6 };
        stack.Children.Add(new TextBlock { Text = value, FontSize = 28, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
        stack.Children.Add(new TextBlock { Text = label, FontSize = 13, Foreground = new SolidColorBrush(Color.FromArgb(255, 102, 112, 133)) });
        grid.Children.Add(new Border { Child = stack, Padding = new Thickness(16), CornerRadius = new CornerRadius(10), Background = new SolidColorBrush(Color.FromArgb(255, 244, 248, 252)) });
        Grid.SetColumn(grid.Children[^1], column);
    }

    private static Color ParseColor(string hex)
    {
        var value = hex.TrimStart('#');
        return Color.FromArgb(255, Convert.ToByte(value[..2], 16), Convert.ToByte(value.Substring(2, 2), 16), Convert.ToByte(value.Substring(4, 2), 16));
    }
}
