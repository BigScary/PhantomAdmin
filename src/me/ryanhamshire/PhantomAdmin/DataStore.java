package me.ryanhamshire.PhantomAdmin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

//singleton class which manages all GriefPrevention data (except for config options)
public class DataStore 
{
    protected final static String dataLayerFolderPath = "plugins" + File.separator + "PhantomAdmin";
    final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
    final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
    
    //in-memory cache for messages
	private String [] messages;
	
	DataStore()
	{
	    this.loadMessages();
	}
	
	private void loadMessages()
	{
	    Messages [] messageIDs = Messages.values();
		this.messages = new String[Messages.values().length];
		
		HashMap<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();
		
		//initialize defaults
		this.addDefault(defaults, Messages.PlayerNotFound, "Player not found.", null);
		this.addDefault(defaults, Messages.NickNameSet, "Updated {0}'s nickname to {1}.", "0: real player name.  1: new nickname.");
		this.addDefault(defaults, Messages.FormatSet, "Updated {0}'s chat format.  Example message follows.", "0: real player name.");
		this.addDefault(defaults, Messages.SampleMessage, "Isn't anonymity great?", "Sample message sent to admins when they use /SetChatFormat");
		this.addDefault(defaults, Messages.PermissionNodeRequired, "You need permission {0} to do that.", "0: permission node required");
		this.addDefault(defaults, Messages.SilentJoin2, "Silent connect: {0}", "0: invisible player's join message");
		this.addDefault(defaults, Messages.SilentQuit2, "Silent disconnect: {0}", "0: invisible player's quit message");
		this.addDefault(defaults, Messages.SilentDeath, "Silent death: {0}", "0: invisible player's death message");
		this.addDefault(defaults, Messages.NoTouchPlateWhileInvisible, "To activate pressure plates while invisible, sneak.", null);
		this.addDefault(defaults, Messages.NoItemPickupWhileInvisible, "To pick up items while invisible, sneak.", null);
		this.addDefault(defaults, Messages.NoItemDropWhileInvisible, "To drop items while invisible, sneak or drag them from your inventory screen.  TIP: Right-click players or containers for direct, silent inventory access.", null);
		this.addDefault(defaults, Messages.ChatLocked2, "You're invisible.  Use /UnlockChat (/uc) to start chatting.", null);
		this.addDefault(defaults, Messages.ChatUnlocked, "Chat unlocked.", null);
		this.addDefault(defaults, Messages.Vanished, "You're now invisible to players who don't have permission to see you.  Use /Appear to become visible.", null);
		this.addDefault(defaults, Messages.Appeared, "You're now visible to all players.  Use /Vanish to disappear again.", null);
		this.addDefault(defaults, Messages.Reloaded, "Reloaded settings from the config file.  If you've updated PhantomAdmin.jar, you still need to reboot or /reload the server.", null);
		this.addDefault(defaults, Messages.NotInvisibleWarning, "Warning: You're not invisible.  You can use /Vanish to disappear.", null);
		this.addDefault(defaults, Messages.FakePlayerNotFound, "Player not found.", null);
		
        //load the config file
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));
		
		//for each message ID
		for(int i = 0; i < messageIDs.length; i++)
		{
			//get default for this message
			Messages messageID = messageIDs[i];
			CustomizableMessage messageData = defaults.get(messageID.name());
			
			//if default is missing, log an error and use some fake data for now so that the plugin can run
			if(messageData == null)
			{
				PhantomAdmin.AddLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
				messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}
			
			//read the message from the file, use default if necessary
			this.messages[messageID.ordinal()] = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
			config.set("Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);
			
			//support color codes
            this.messages[messageID.ordinal()] = this.messages[messageID.ordinal()].replace('$', (char)0x00A7);
			
			if(messageData.notes != null)
			{
				messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
				config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
			}
		}
		
		//save any changes
		try
		{
		    config.options().header("Use a YAML editor like NotepadPlusPlus to edit this file.  \nAfter editing, back up your changes before reloading the server in case you made a syntax error.  \nUse dollar signs ($) for formatting codes, which are documented here: http://minecraft.gamepedia.com/Formatting_codes");
			config.save(DataStore.messagesFilePath);
		}
		catch(IOException exception)
		{
		    PhantomAdmin.AddLogEntry("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}
		
		defaults.clear();
		System.gc();				
	}

	private void addDefault(HashMap<String, CustomizableMessage> defaults, Messages id, String text, String notes)
	{
		CustomizableMessage message = new CustomizableMessage(id, text, notes);
		defaults.put(id.name(), message);		
	}

	synchronized public String getMessage(Messages messageID, String... args)
	{
		String message = messages[messageID.ordinal()];
		
		for(int i = 0; i < args.length; i++)
		{
			String param = args[i];
			message = message.replace("{" + i + "}", param);
		}
		
		return message;		
	}
}
