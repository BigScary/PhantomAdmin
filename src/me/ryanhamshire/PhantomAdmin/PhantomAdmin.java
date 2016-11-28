//Copyright Ryan Hamshire 2015

package me.ryanhamshire.PhantomAdmin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PhantomAdmin extends JavaPlugin
{
	//for convenience, a reference to the instance of this plugin
	public static PhantomAdmin instance;
	
	//for logging to the console and log file
	private static Logger log = Logger.getLogger("Minecraft");
	
	//adds a server log entry
	public static void AddLogEntry(String entry)
	{
		log.info("PhantomAdmin: " + entry);
	}

    //config settings
	String config_defaultChatFormat;
    String config_defaultNickname;
    String config_whisperFormatSend;
    String config_whisperFormatReceive;
    CommandList config_whisperCommandList;
    String config_whisperCommandNames;
    boolean config_anonymousWhenVisible;
    boolean config_invisibleByDefault;
    boolean config_alwaysJoinLeaveSilently;
    boolean config_warnOnVisibleJoin;
    boolean config_accessInventoryWhileVisible;
    boolean config_anonymityEnabled;

    //datastore
    DataStore datastore;

    ConcurrentHashMap<UUID, AnonymityInfo> nicknameMap = new ConcurrentHashMap<UUID, AnonymityInfo>();

    //initializes well...   everything
	public void onEnable()
	{ 		
		instance = this;
		
		this.loadConfig();
		
		@SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>)(Bukkit.getServer().getOnlinePlayers());
		for(Player player : players)
		{
		    this.handlePlayerLogin(player);
		}
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		PAEventHandler eventHandler = new PAEventHandler();
		pluginManager.registerEvents(eventHandler, this);
		
		AddLogEntry("PhantomAdmin enabled.");
	}
	
	private void loadConfig()
	{
	    //load the config if it exists
	    FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
        
        //read configuration settings (note defaults)
        this.config_defaultChatFormat = config.getString("Anonymity.Default Chat Message Format", "$f[%nickname%]$f %message%");
        this.config_defaultNickname = config.getString("Anonymity.Default Nickname", "Server");
        this.config_anonymousWhenVisible = config.getBoolean("Anonymity.Use Nickname When Visible", true);
        this.config_whisperFormatSend = config.getString("Anonymity.Whisper Message Format.Send", "$7$oYou whisper to %name%$7$o: %message%");
        this.config_whisperFormatReceive = config.getString("Anonymity.Whisper Message Format.Receive", "$7$o%name%$7$o whispers to you: %message%");
        this.config_whisperCommandNames = config.getString("Anonymity.Whisper Commands", "/tell;/pm;/whisper;/msg");
        this.config_whisperCommandList = new CommandList(this.config_whisperCommandNames);
        this.config_invisibleByDefault = config.getBoolean("Invisibility.Invisible By Default", true);
        this.config_alwaysJoinLeaveSilently = config.getBoolean("Invisibility.Always Join and Leave Silently", true);
        this.config_warnOnVisibleJoin = config.getBoolean("Invisibility.Warn After Joining Visibly", true);
        this.config_accessInventoryWhileVisible = config.getBoolean("Invisibility.Access Player Inventories While Visible", false);
        this.config_anonymityEnabled = config.getBoolean("Anonymity.Feature Set Enabled", true);
        
        this.nicknameMap.clear();
        Set<String> uuids;
        ConfigurationSection section = config.getConfigurationSection("Anonymity.Players");
        if(section != null)
        {
            uuids = section.getKeys(false);
        }
        else
        {
            uuids = new HashSet<String>();
        }
        
        for(String uuid : uuids)
        {
            UUID playerID = null;
            String realName = "???";
            try
            {
                playerID = UUID.fromString(uuid);
            }
            catch(IllegalArgumentException e)
            {
                AddLogEntry("Invalid UUID in config file (not the correct UUID format): " + uuid);
            }
            
            OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(playerID);
            if(player == null)
            {
                AddLogEntry("Config file warning... no player with this UUID has played on this server before: " + uuid);
            }
            else
            {
                realName = player.getName();
            }
            
            String nickname = config.getString("Anonymity.Players." + uuid + ".Nickname", this.config_defaultNickname);
            String chatFormat = config.getString("Anonymity.Players." + uuid + ".Chat Message Format", this.config_defaultChatFormat);
                        
            this.nicknameMap.put(playerID, new AnonymityInfo(nickname, chatFormat, realName));
        }
        
        this.writeConfig();
        this.datastore = new DataStore();
    }
	
	void writeConfig()
	{
	    FileConfiguration config = new YamlConfiguration();
	    
	    config.set("Invisibility.Invisible By Default", this.config_invisibleByDefault);
        config.set("Invisibility.Always Join and Leave Silently", this.config_alwaysJoinLeaveSilently);
        config.set("Invisibility.Warn After Joining Visibly", this.config_warnOnVisibleJoin);
        config.set("Invisibility.Access Player Inventories While Visible", this.config_accessInventoryWhileVisible);
        config.set("Anonymity.Feature Set Enabled", this.config_anonymityEnabled);
        config.set("Anonymity.Default Chat Message Format", this.config_defaultChatFormat);
	    config.set("Anonymity.Use Nickname When Visible", this.config_anonymousWhenVisible);
	    config.set("Anonymity.Default Nickname", this.config_defaultNickname);
        config.set("Anonymity.Whisper Message Format.Send", this.config_whisperFormatSend);
        config.set("Anonymity.Whisper Message Format.Receive", this.config_whisperFormatReceive);
        config.set("Anonymity.Whisper Commands", this.config_whisperCommandNames);
        
	    for(UUID key : this.nicknameMap.keySet())
	    {
            OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(key);
            if(player == null || player.getName() == null || player.getName().isEmpty())
            {
                config.set("Anonymity.Players." + key + ".Real Name", "??? (No name found for this UUID in world data.)");
            }
            else
            {
                config.set("Anonymity.Players." + key + ".Real Name", player.getName());
            }
            
            AnonymityInfo value = this.nicknameMap.get(key);
            config.set("Anonymity.Players." + key + ".Nickname", value.nickname);
            config.set("Anonymity.Players." + key + ".Chat Message Format", value.chatFormat);
	    }
	    
	    try
        {
            config.save(new File(DataStore.configFilePath));
        }
        catch(IOException e)
        {
            AddLogEntry("Failed to save config settings to the config file:\n" + e.toString());
        }
	}
	
	void setNickname(Player player, String nickname)
	{
		//support color codes
		nickname = nickname.replace('&', (char)0x00A7);
		
		AnonymityInfo info = this.nicknameMap.get(player.getUniqueId());
	    if(info == null)
	    {
	        info = new AnonymityInfo(this.config_defaultNickname, this.config_defaultChatFormat, player.getName());
	    }
	    info.nickname = nickname;
	    this.nicknameMap.put(player.getUniqueId(), info);
	    this.writeConfig();
	}
	
	void setChatFormat(Player player, String chatFormat)
	{
	    AnonymityInfo info = this.nicknameMap.get(player.getUniqueId());
	    if(info == null)
        {
            info = new AnonymityInfo(this.config_defaultNickname, this.config_defaultChatFormat, player.getName());
        }
	    info.chatFormat = chatFormat;
        this.nicknameMap.put(player.getUniqueId(), info);
        this.writeConfig();
	}
	
	void handlePlayerLogin(Player player)
	{
	    if(player.hasPermission("phantomadmin.anonymous") && this.nicknameMap.get(player.getUniqueId()) == null)
	    {
	        AnonymityInfo info = new AnonymityInfo(this.config_defaultNickname, this.config_defaultChatFormat, player.getName());
	        this.nicknameMap.put(player.getUniqueId(), info);
	        this.writeConfig();
	    }
	    
	    //if new player is invisible, hide from any online players who can't see invis
	    if(this.isInvisible(player))
        {
	        this.hidePlayer(player);
        }
	    
	    if(this.wantsAnonymity(player))
	    {
	        this.hideName(player);
	    }
	    
        //if new player can't see invis, hide any invisible players
        if(!player.hasPermission("phantomadmin.seeinvisible"))
        {
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
            for(Player onlinePlayer : players)
            {
                if(this.isInvisible(onlinePlayer))
                {
                    player.hidePlayer(onlinePlayer);
                }
            }
        }
	}
	
	void hideName(Player player)
	{
	    String nickname = this.nicknameMap.get(player.getUniqueId()).nickname;
        player.setDisplayName(nickname);
        player.setCustomName(nickname);
        player.setCustomNameVisible(true);
        player.setPlayerListName(nickname);
    }
	
	void showName(Player player)
    {
        String name = player.getName();
	    player.setDisplayName(name);
        player.setCustomName(name);
        player.setCustomNameVisible(true);
        player.setPlayerListName(name);
    }

    boolean wantsAnonymity(Player player)
	{
	    if(!this.config_anonymityEnabled) return false;
        
        if(!player.hasPermission("phantomadmin.anonymous") || !this.nicknameMap.containsKey(player.getUniqueId())) return false;
	    
	    if(!this.config_anonymousWhenVisible && !this.isInvisible(player))
	    {
	        return false;
	    }
	    else
	    {
	        return true;
	    }
	}
	
	boolean isInvisible(Player player)
	{
	    if(!player.hasPermission("phantomadmin.invisible")) return false;
	    
	    PlayerData data = PlayerData.FromPlayer(player);
	    return data.invisible;
	}
	
	void hidePlayer(Player player)
	{
	    @SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
        for(Player onlinePlayer : players)
        {
            if(!onlinePlayer.hasPermission("phantomadmin.seeinvisible"))
            {
                onlinePlayer.hidePlayer(player);
            }
        }
        
        PlayerData data = PlayerData.FromPlayer(player);
        data.invisible = true;
        
        if(this.wantsAnonymity(player))
        {
            this.hideName(player);
        }
	}
	
	void showPlayer(Player player)
	{
	    @SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
        for(Player onlinePlayer : players)
        {
            onlinePlayer.showPlayer(player);
        }
	    
	    PlayerData data = PlayerData.FromPlayer(player);
        data.invisible = false;
        
        if(!this.wantsAnonymity(player))
        {
            this.showName(player);
        }
	}

    public void onDisable()
	{
		
	}
	
	//handles slash commands
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
	    Player player = null;
        if (sender instanceof Player) 
        {
            player = (Player) sender;
        }
        
        if(cmd.getName().equalsIgnoreCase("setnickname"))
        {
            if(args.length < 2) return false;
            
            Player target = this.resolvePlayerByName(args[0]);
            if(target == null)
            {
                sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                return true;
            }
            
            if(!target.getName().equals(player.getName()) && !player.hasPermission("phantomadmin.setnicknameother"))
            {
                sendMessage(player, TextMode.Err, Messages.PermissionNodeRequired, "phantomadmin.setnicknameother");
                return true;
            }
            
            this.setNickname(target, args[1]);
            sendMessage(player, TextMode.Success, Messages.NickNameSet, target.getName(), args[1]);
            if(this.isInvisible(target))
            {
            	this.hideName(target);
            }
            
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("setchatformat"))
        {
            if(args.length < 2) return false;
            
            Player target = this.resolvePlayerByName(args[0]);
            if(target == null)
            {
                sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                return true;
            }
            
            if(!target.getName().equals(player.getName()) && !player.hasPermission("phantomadmin.setchatformatother"))
            {
                sendMessage(player, TextMode.Err, Messages.PermissionNodeRequired, "phantomadmin.setchatformatother");
                return true;
            }
            
            StringBuilder formatBuilder = new StringBuilder();
            for(int i = 1; i < args.length; i++)
            {
                formatBuilder.append(args[i] + " ");
            }
            
            this.setChatFormat(target, formatBuilder.toString().trim());
            sendMessage(player, TextMode.Success, Messages.FormatSet, target.getName(), args[1]);
            player.sendMessage(this.formatChatMessage(target, this.datastore.getMessage(Messages.SampleMessage)));
            
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("unlockchat") && player != null)
        {
            PlayerData data = PlayerData.FromPlayer(player);
            data.chatLocked = false;
            PhantomAdmin.sendMessage(player, TextMode.Success, Messages.ChatUnlocked);
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("vanish") && player != null)
        {
            this.hidePlayer(player);
            PhantomAdmin.sendMessage(player, TextMode.Success, Messages.Vanished);
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("appear") && player != null)
        {
            this.showPlayer(player);
            PhantomAdmin.sendMessage(player, TextMode.Success, Messages.Appeared);
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("playerinventory") && player != null)
        {
            if(args.length < 1) return false;
            
            Player target = this.resolvePlayerByName(args[0]);
            if(target == null)
            {
                sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                return true;
            }
            
            player.openInventory(target.getInventory());
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("enderinventory") && player != null)
        {
            if(args.length < 1) return false;
            
            Player target = this.resolvePlayerByName(args[0]);
            if(target == null)
            {
                sendMessage(player, TextMode.Err, Messages.PlayerNotFound);
                return true;
            }
            
            player.openInventory(target.getEnderChest());
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("whois"))
        {
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>)this.getServer().getOnlinePlayers();
            
            StringBuilder builder = new StringBuilder();
            for(Player p : players)
            {
                if(this.wantsAnonymity(p))
                {
                    AnonymityInfo info = this.nicknameMap.get(p.getUniqueId());
                    if(info != null && info.nickname != null)
                    {
                        builder.append(info.nickname + "=" + p.getName() + " ");
                    }
                }
            }
            
            PhantomAdmin.sendMessage(player, TextMode.Info, builder.toString());
            
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("pareload"))
        {
            this.loadConfig();
            PhantomAdmin.sendMessage(player, TextMode.Success, Messages.Reloaded);
            return true;
        }
        
        else if(cmd.getName().equalsIgnoreCase("tell"))
        {
            if(args.length < 2) return false;
            
            String targetName = args[0];
            @SuppressWarnings("deprecation")
			Player target = this.getServer().getPlayerExact(targetName);
            if(target == null || (player != null && !player.canSee(target)))
            {
            	PhantomAdmin.sendMessage(player, TextMode.Err, Messages.FakePlayerNotFound);
            	return true;
            }
            
            StringBuilder contentBuilder = new StringBuilder();
            for(int i = 1; i < args.length; i++)
            {
                contentBuilder.append(args[i]).append(" ");
            }
            String whisperContent = contentBuilder.toString().trim();
            
            String outgoingConfirmationMessage = PhantomAdmin.instance.config_whisperFormatSend.replace('$', (char)0x00A7).replace("%name%", target.getName()).replace("%message%", whisperContent);
            PhantomAdmin.sendMessage(player, TextMode.Info, outgoingConfirmationMessage);

            String senderName = "Server";
            if(player != null) senderName = player.getDisplayName();
            String incomingNotificationMessage = PhantomAdmin.instance.config_whisperFormatReceive.replace('$', (char)0x00A7).replace("%name%", senderName).replace("%message%", whisperContent);
            target.sendMessage(incomingNotificationMessage);

            return true;
        }
        
        return false;
	}
	
	//sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, Messages messageID, String... args)
    {
        sendMessage(player, color, messageID, 0, args);
    }
    
    //sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, Messages messageID, long delayInTicks, String... args)
    {
        String message = PhantomAdmin.instance.datastore.getMessage(messageID, args);
        sendMessage(player, color, message);
    }
    
    //sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, String message)
    {
        if(message == null || message.length() == 0) return;
        
        if(player == null)
        {
            PhantomAdmin.AddLogEntry(color + message);
        }
        else
        {
            player.sendMessage(color + message);
        }
    }
    
    @SuppressWarnings("deprecation")
    Player resolvePlayerByName(String name) 
    {
        //try online players first
        Player targetPlayer = this.getServer().getPlayerExact(name);
        if(targetPlayer != null) return targetPlayer;
        
        targetPlayer = this.getServer().getPlayer(name);
        if(targetPlayer != null) return targetPlayer;
        
        return null;
    }

    public String formatChatMessage(Player player, String message)
    {
        AnonymityInfo info = PhantomAdmin.instance.nicknameMap.get(player.getUniqueId());
        if(info == null) return null;
        
        String returnValue = info.chatFormat.replace('$', (char)0x00A7).replace("%nickname%", info.nickname).replace("%message%", message);
        
        return returnValue;
    }
}