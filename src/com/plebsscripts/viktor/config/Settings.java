package com.plebsscripts.viktor.config;

public class Settings {
    // INPUT
    public String inputPath = "ge_flips.csv";

    // GE / runtime
    public int offerSlots = 8;
    public int maxGpInFlight = 15_000_000;
    public int buyStaleMinutes = 12;
    public int sellStaleMinutes = 30;
    public int reprobeMinMinutes = 60;

    // Coordinator
    public boolean enableCoordinator = true;
    public String coordinatorUrl = "http://127.0.0.1:3030";
    public String botId = "";

    // Discord
    public DiscordBlock discord = new DiscordBlock();
    public static class DiscordBlock {
        public boolean enabled = false;
        public String webhookUrl = "";
        public boolean sendProbe = true, sendTrades = true, sendLimits = true, sendErrors = true;
        public int minTradeMarginGp = 15;
        public String discordWebhook = "";
    }

    public boolean discordEnabled()    { return discord != null && discord.enabled; }
    public boolean discordSendProbe()  { return discordEnabled() && discord.sendProbe; }
    public boolean discordSendTrades() { return discordEnabled() && discord.sendTrades; }
    public boolean discordSendLimits() { return discordEnabled() && discord.sendLimits; }
    public boolean discordSendErrors() { return discordEnabled() && discord.sendErrors; }
    public String  discordWebhookUrl() { return discord.webhookUrl; }

    public String getAccountName() { return "Account"; } // TODO: pull from client if available
    public String getBotId() { return (botId == null || botId.isEmpty()) ? getAccountName() : botId; }
}
