package com.plebsscripts.viktor.config;

public class Settings {
    // INPUT
    public String inputPath = "data/items.csv";

    // GE / runtime
    public int offerSlots = 8;
    public int maxGpInFlight = 15_000_000;
    public int buyStaleMinutes = 12;
    public int sellStaleMinutes = 30;
    public int reprobeMinMinutes = 60;
    public int probeStaleMinutes = 60; // Re-probe every 60 minutes

    // ADD: Budget limits (used by AppGUI)
    public long maxGpPerFlip = 250_000;
    public boolean respectLimits = true;

    // Coordinator
    public boolean enableCoordinator = true; //
    public String coordinatorUrl = "http://127.0.0.1:8888"; // CHANGED: Match CoordinatorServer default port
    public String botId = "";

    // Discord
    public DiscordBlock discord = new DiscordBlock();
    public double kellyFraction = 0.25; // Default quarter Kelly

    public static class DiscordBlock {
        public boolean enabled = false;
        public String webhookUrl = "";
        public boolean sendProbe = true;
        public boolean sendTrades = true;
        public boolean sendLimits = true;
        public boolean sendErrors = true;
        public int minTradeMarginGp = 15;

        // DEPRECATED: Use webhookUrl instead
        @Deprecated
        public String discordWebhook = "";
    }

    public String discordWebhook() {
        // Support legacy field name
        if (discord != null && discord.webhookUrl != null && !discord.webhookUrl.isEmpty()) {
            return discord.webhookUrl;
        }
        if (discord != null && discord.discordWebhook != null && !discord.discordWebhook.isEmpty()) {
            return discord.discordWebhook;
        }
        return "";
    }

    // ADD: Anti-ban settings
    public AntiBanBlock antiBan = new AntiBanBlock();

    public static class AntiBanBlock {
        public boolean enabled = true;
        public int minDelayMs = 600;
        public int maxDelayMs = 2400;
        public boolean randomMouseMovements = true;
        public boolean randomCameraRotation = false;
        public int afkBreakChance = 5; // % chance per hour
    }

    // ADD: Pastebin hot reload (future feature)
    public HotReloadBlock hotReload = new HotReloadBlock();

    public static class HotReloadBlock {
        public boolean enabled = false;
        public String pastebinUrl = "";
        public int checkIntervalSeconds = 30;
    }

    // Discord helper methods
    public boolean discordEnabled()    { return discord != null && discord.enabled; }
    public boolean discordSendProbe()  { return discordEnabled() && discord.sendProbe; }
    public boolean discordSendTrades() { return discordEnabled() && discord.sendTrades; }
    public boolean discordSendLimits() { return discordEnabled() && discord.sendLimits; }
    public boolean discordSendErrors() { return discordEnabled() && discord.sendErrors; }

    public String discordWebhookUrl() {
        // Support legacy field name
        if (discord.webhookUrl != null && !discord.webhookUrl.isEmpty()) {
            return discord.webhookUrl;
        }
        return discord.discordWebhook;
    }

    // Account identification
    public String getAccountName() {
        // TODO: Try to get from DreamBot client
        // For now, use botId or default
        if (botId != null && !botId.isEmpty()) {
            return botId;
        }
        return "Account1";
    }

    public String getBotId() {
        return (botId == null || botId.isEmpty()) ? getAccountName() : botId;
    }

    // ADD: Validation method
    public boolean isValid() {
        if (inputPath == null || inputPath.isEmpty()) {
            return false;
        }
        if (maxGpInFlight < 100_000) {
            return false;
        }
        if (offerSlots < 1 || offerSlots > 8) {
            return false;
        }
        return true;
    }

    // ADD: Clone method for profile duplication
    public Settings clone() {
        Settings copy = new Settings();
        copy.inputPath = this.inputPath;
        copy.offerSlots = this.offerSlots;
        copy.maxGpInFlight = this.maxGpInFlight;
        copy.buyStaleMinutes = this.buyStaleMinutes;
        copy.sellStaleMinutes = this.sellStaleMinutes;
        copy.reprobeMinMinutes = this.reprobeMinMinutes;
        copy.maxGpPerFlip = this.maxGpPerFlip;
        copy.respectLimits = this.respectLimits;
        copy.enableCoordinator = this.enableCoordinator;
        copy.coordinatorUrl = this.coordinatorUrl;
        copy.botId = this.botId;

        // Deep copy Discord settings
        copy.discord = new DiscordBlock();
        copy.discord.enabled = this.discord.enabled;
        copy.discord.webhookUrl = this.discord.webhookUrl;
        copy.discord.sendProbe = this.discord.sendProbe;
        copy.discord.sendTrades = this.discord.sendTrades;
        copy.discord.sendLimits = this.discord.sendLimits;
        copy.discord.sendErrors = this.discord.sendErrors;
        copy.discord.minTradeMarginGp = this.discord.minTradeMarginGp;

        return copy;
    }

    // ADD: toString for debugging
    @Override
    public String toString() {
        return String.format("Settings{account=%s, csv=%s, maxGP=%d, coordinator=%s}",
                getAccountName(), inputPath, maxGpInFlight, enableCoordinator);
    }
}
