//Copyright Ryan Hamshire 2015

package me.ryanhamshire.PhantomAdmin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

class PAEventHandler implements Listener
{
	private HashMap<String, HashSet<Inventory>> inventorySyncMap = new HashMap<String, HashSet<Inventory>>();
	private HashSet<Inventory> phantomInventories = new HashSet<Inventory>();
	
    PAEventHandler() { }
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	synchronized void onPlayerChat(AsyncPlayerChatEvent event)
	{
	    Player player = event.getPlayer();
	    if(player == null) return;
	    
	    if(PhantomAdmin.instance.isInvisible(player))
	    {
	        PlayerData data = PlayerData.FromPlayer(player);
	        if(data.chatLocked)
	        {
	            event.setCancelled(true);
	            PhantomAdmin.sendMessage(player, TextMode.Instr, Messages.ChatLocked2);
	            return;
	        }
	    }
	    
	    if(PhantomAdmin.instance.wantsAnonymity(player))
	    {
    	    String message = PhantomAdmin.instance.formatChatMessage(player, event.getMessage());
    	    
            if(message != null)
            {
                event.setCancelled(true);
                
        	    for(Player recipient : event.getRecipients())
        	    {
        	        recipient.sendMessage(message);
        	    }
        	    
        	    PhantomAdmin.AddLogEntry("(" + player.getName() + ")" + message);
            }
	    }
	}
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    synchronized void onEntityGotTarget(EntityTargetLivingEntityEvent event)
    {
	    if(event.getEntityType() == EntityType.EXPERIENCE_ORB)
	    {
	        LivingEntity target = event.getTarget();
	        if(target.getType() == EntityType.PLAYER)
            {
                Player player = (Player)target;
                if(PhantomAdmin.instance.isInvisible(player) && player.getGameMode() != GameMode.SURVIVAL)
                {
                    Entity entity = event.getEntity();
                    entity.remove();
                }
            }
	    }
	    else
	    {
	        LivingEntity target = event.getTarget();
	        if(target != null && target.getType() == EntityType.PLAYER)
	        {
	            Player player = (Player)target;
	            if(PhantomAdmin.instance.isInvisible(player) && player.getGameMode() != GameMode.SURVIVAL)
	            {
	                event.setCancelled(true);
	                return;
	            }
	        }
	    }
    }
	
	@SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onPlayerCommandPreprocess (PlayerCommandPreprocessEvent event)
    {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String [] args = message.split(" ");
        if(args.length < 2) return;
        
        Player target = null;
        String firstArg = args[1];
        boolean searchedTarget = false;
        
        //unless player can see invisible, none of his commands can target an invisible player 
        if(!player.hasPermission("phantomadmin.seeinvisible"))
        {
            target = Bukkit.getServer().getPlayerExact(firstArg);
            
            searchedTarget = true;
            
            if(target != null && PhantomAdmin.instance.isInvisible(target) && !target.getUniqueId().equals(player.getUniqueId()))
            {
                String nickname = "";
                AnonymityInfo info = PhantomAdmin.instance.nicknameMap.get(target.getUniqueId());
                if(info != null)
                {
                    nickname = info.nickname;
                }
                
                if(!target.getDisplayName().equalsIgnoreCase(nickname) && !target.getName().equalsIgnoreCase(nickname))
                {
                    event.setCancelled(true);
                    PhantomAdmin.sendMessage(player, TextMode.Err, Messages.FakePlayerNotFound);
                    return;
                }
            }
        }
        
        String command = args[0].toLowerCase();
        if(player.hasPermission("phantomadmin.seeinvisible") && args.length > 1 && PhantomAdmin.instance.config_whisperCommandList.Contains(command))
        {
            boolean useAnonymousWhisper = false;
            String senderName = null;
            ArrayList<Player> recipientList = new ArrayList<Player>();
            
            //if sender is anonymous
            if(PhantomAdmin.instance.wantsAnonymity(player))
            {
                senderName = PhantomAdmin.instance.nicknameMap.get(player.getUniqueId()).nickname;
                useAnonymousWhisper = true;
            }
            else
            {
                senderName = player.getName();
            }
            
            //resolve target if not already done above
            if(!searchedTarget)
            {
                target = Bukkit.getServer().getPlayerExact(firstArg);
                searchedTarget = true;
            }
            
            if(target != null)
            {
                recipientList.add(target);
                firstArg = target.getName();  //correct casing
            }
            else
            {
                for(UUID uuid : PhantomAdmin.instance.nicknameMap.keySet())
                {
                    String nickname = PhantomAdmin.instance.nicknameMap.get(uuid).nickname;
                    if(firstArg.equalsIgnoreCase(nickname))
                    {
                        firstArg = nickname;  //correct casing
                        useAnonymousWhisper = true;
                        OfflinePlayer phantom = Bukkit.getOfflinePlayer(uuid);
                        if(phantom.isOnline())
                        {
                            recipientList.add(phantom.getPlayer());
                        }
                    }
                }
            }
            
            if(useAnonymousWhisper)
            {
                event.setCancelled(true);
                StringBuilder contentBuilder = new StringBuilder();
                for(int i = 2; i < args.length; i++)
                {
                    contentBuilder.append(args[i]).append(" ");
                }
                String whisperContent = contentBuilder.toString().trim();
                
                String outgoingConfirmationMessage = PhantomAdmin.instance.config_whisperFormatSend.replace('$', (char)0x00A7).replace("%name%", firstArg).replace("%message%", whisperContent);
                player.sendMessage(outgoingConfirmationMessage);
                String incomingNotificationMessage = PhantomAdmin.instance.config_whisperFormatReceive.replace('$', (char)0x00A7).replace("%name%", senderName).replace("%message%", whisperContent);
                for(Player recipient : recipientList)
                {
                    recipient.sendMessage(incomingNotificationMessage);
                }
                
                return;
            }
        }
        
        //unless player can see invisible, none of his commands may ever target an anonymous player's real name
        if(!player.hasPermission("phantomadmin.seeinvisible") && args.length > 1)
        {
            if(!searchedTarget)
            {
                target = Bukkit.getServer().getPlayerExact(args[1]);
                searchedTarget = true;
            }
            
            if(target == null)
            {
                for(AnonymityInfo info : PhantomAdmin.instance.nicknameMap.values())
                {
                    if(args[1].equalsIgnoreCase(info.realName))
                    {
                        event.setCancelled(true);
                        PhantomAdmin.sendMessage(player, TextMode.Err, Messages.FakePlayerNotFound);
                        return;
                    }
                }
            }
            else if(!target.getName().equals(player.getName()))
            {
                if(PhantomAdmin.instance.isInvisible(target))
                {
                    event.setCancelled(true);
                    PhantomAdmin.sendMessage(player, TextMode.Err, Messages.FakePlayerNotFound);
                    return;
                }
            }
        }
    }
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlayerBreak(BlockBreakEvent event)
    {
	    Player player = event.getPlayer();
	    if(player.getGameMode() == GameMode.CREATIVE && PhantomAdmin.instance.isInvisible(player))
        {
            event.setCancelled(true);
            Block block = event.getBlock();
            block.setType(Material.AIR);
        }
    }
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	void onPlayerJoin(PlayerJoinEvent event)
	{
	    Player player = event.getPlayer();

	    PhantomAdmin.instance.handlePlayerLogin(player);
	    
	    if(!player.hasPermission("phantomadmin.invisible") || !player.hasPermission("phantomadmin.silentjoinleave")) return;
	    
	    if(PhantomAdmin.instance.config_invisibleByDefault || PhantomAdmin.instance.isInvisible(player))
	    {
	        PhantomAdmin.instance.hidePlayer(player);
	    }
	    else if(PhantomAdmin.instance.config_warnOnVisibleJoin)
	    {
	        PhantomAdmin.sendMessage(player, TextMode.Warn, Messages.NotInvisibleWarning);
	    }
	    
	    if(PhantomAdmin.instance.config_invisibleByDefault || PhantomAdmin.instance.config_alwaysJoinLeaveSilently || PhantomAdmin.instance.isInvisible(player))
	    {
    	    String joinMessage = event.getJoinMessage();
            if(joinMessage == null)
            {
                joinMessage = player.getName();
            }
            event.setJoinMessage("");
            
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
            for(Player onlinePlayer : players)
            {
                if(onlinePlayer.hasPermission("phantomadmin.seeinvisible"))
                {
                    PhantomAdmin.sendMessage(onlinePlayer, TextMode.Info, Messages.SilentJoin2, joinMessage);
                }
            }
	    }
	}
	
	@EventHandler(ignoreCancelled = true)
    void onServerListPing(ServerListPingEvent event)
    {
        Iterator<Player> players = event.iterator();
        while(players.hasNext())
        {
            Player player = players.next();
            if(PhantomAdmin.instance.isInvisible(player))
            {
                players.remove();
            }
        }
    }
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPlayerQuit(PlayerQuitEvent event)
    {
	    Player player = event.getPlayer();
	    if(!player.hasPermission("phantomadmin.invisible") || !player.hasPermission("phantomadmin.silentjoinleave")) return;
	    
        if(PhantomAdmin.instance.config_alwaysJoinLeaveSilently || PhantomAdmin.instance.isInvisible(player))
        {
            String quitMessage = event.getQuitMessage();
            if(quitMessage == null)
            {
                quitMessage = player.getName();
            }
            event.setQuitMessage("");
            
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
            for(Player onlinePlayer : players)
            {
                if(onlinePlayer.hasPermission("phantomadmin.seeinvisible"))
                {
                    PhantomAdmin.sendMessage(onlinePlayer, TextMode.Info, Messages.SilentQuit2, quitMessage);
                }
            }
        }
    }
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onPlayerDeath(PlayerDeathEvent event)
    {
        Player player = event.getEntity();
        if(PhantomAdmin.instance.isInvisible(player))
        {
            String message = event.getDeathMessage();
            event.setDeathMessage("");
            
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>)Bukkit.getServer().getOnlinePlayers();
            for(Player onlinePlayer : players)
            {
                if(onlinePlayer.hasPermission("phantomadmin.seeinvisible"))
                {
                    PhantomAdmin.sendMessage(onlinePlayer, TextMode.Info, Messages.SilentDeath, message);
                }
            }
        }
    }
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlayerPickupItem(PlayerPickupItemEvent event)
    {
	    Player player = event.getPlayer();
	    if(!player.isSneaking() && PhantomAdmin.instance.isInvisible(player))
	    {
	        event.setCancelled(true);
	        PlayerData data = PlayerData.FromPlayer(player);
            if(!data.gotItemPickupInfo)
            {
                PhantomAdmin.sendMessage(player, TextMode.Warn, Messages.NoItemPickupWhileInvisible);
                data.gotItemPickupInfo = true;
            }
            
	        return;
	    }
    }
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	void onPlayerInteractEntity(PlayerInteractEntityEvent event)
	{
	    Player player = event.getPlayer();
	    Entity entity = event.getRightClicked();
	    if(entity instanceof Player && player.hasPermission("phantomadmin.inventoryaccess"))
	    {
	        if(PhantomAdmin.instance.config_accessInventoryWhileVisible || PhantomAdmin.instance.isInvisible(player))
	        {
	            Player target = (Player)entity;    
	            player.openInventory(target.getInventory());
	        }
	    }
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlayerDropItem(PlayerDropItemEvent event)
    {
        Player player = event.getPlayer();
        if(!player.isSneaking() && PhantomAdmin.instance.isInvisible(player))
        {
            event.setCancelled(true);
            PlayerData data = PlayerData.FromPlayer(player);
            if(!data.gotItemDropInfo)
            {
                PhantomAdmin.sendMessage(player, TextMode.Warn, Messages.NoItemDropWhileInvisible);
                data.gotItemDropInfo = true;
            }
            return;
        }
    }
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlayerInteractContainer(PlayerInteractEvent event)
    {
	    Action action = event.getAction();
	    if(action != Action.RIGHT_CLICK_BLOCK) return;
	    
	    Player player = event.getPlayer();
	    if(player.isSneaking()) return;
	    if(!PhantomAdmin.instance.isInvisible(player)) return;
	    
	    Block clickedBlock = event.getClickedBlock();
	    BlockState state = clickedBlock.getState();
	    if(!(state instanceof InventoryHolder)) return;
	    
	    String phantomIdentifier = this.getPhantomIdentifier(((InventoryHolder)state).getInventory().getHolder());
	    if(phantomIdentifier != null)
	    {
	        event.setCancelled(true);
	        InventoryHolder holder = (InventoryHolder)state;
	        Inventory inventory = holder.getInventory();
	        Inventory phantomInventory = PhantomAdmin.instance.getServer().createInventory(inventory.getHolder(), inventory.getSize());
            phantomInventory.setContents(inventory.getContents());
            player.openInventory(phantomInventory);
            HashSet<Inventory> allInventories = this.inventorySyncMap.get(inventory);
            if(allInventories == null)
            {
                allInventories = new HashSet<Inventory>();
            }
            allInventories.add(inventory);
            allInventories.add(phantomInventory);
            this.inventorySyncMap.put(phantomIdentifier, allInventories);
            this.phantomInventories.add(phantomInventory);
	    }
    }
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	void onInventoryClose(InventoryCloseEvent event)
	{
	    Inventory inventory = event.getInventory();
	    InventoryHolder holder = inventory.getHolder();
	    if(holder == null) return;
	    String phantomID = this.getPhantomIdentifier(holder);
	    if(this.phantomInventories.contains(inventory))
	    {
	        HashSet<Inventory> originalSet = this.inventorySyncMap.get(phantomID);
	        originalSet.remove(inventory);
	        if(originalSet.size() == 1)
	        {
	            this.inventorySyncMap.remove(phantomID);
	        }
	        this.phantomInventories.remove(inventory);
	    }
	    else if(inventory.getViewers().size() == 1)
	    {
	        HashSet<Inventory> phantoms = this.inventorySyncMap.get(phantomID);
	        if(phantoms != null)
	        {
	            for(Inventory phantom : phantoms)
	            {
	                if(phantom.getViewers().size() > 0) return;
	            }
	            
	            this.inventorySyncMap.remove(phantomID);
	        }
	    }
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onInventoryClick(InventoryClickEvent event)
    {
        this.onInventoryChange(event.getInventory());
    }
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onInventoryDrag(InventoryDragEvent event)
    {
        this.onInventoryChange(event.getInventory());
    }
	
	void onInventoryChange(Inventory inventory)
	{
	    HashSet<Inventory> listeners = inventorySyncMap.get(getPhantomIdentifier(inventory.getHolder()));
	    if(listeners != null && listeners.size() > 0)
	    {
	        Bukkit.getScheduler().runTaskLater(PhantomAdmin.instance, new InventoryCopyTask(inventory, listeners), 1L);
	    }
	}
	
	private class InventoryCopyTask implements Runnable
	{
	    Inventory inventory;
        HashSet<Inventory> listeners;
	    
	    public InventoryCopyTask(Inventory inventory, HashSet<Inventory> listeners)
        {
            this.inventory = inventory;
            this.listeners = listeners;
        }

        public void run()
        {
            ItemStack [] contents = inventory.getContents();
            for(Inventory listener : listeners)
            {
                listener.setContents(contents);
            }
        }
    }
	
	private String getPhantomIdentifier(InventoryHolder holder)
	{
        if(holder instanceof DoubleChest)
        {
            return "Double Chest:" + ((DoubleChest)holder).getLocation().toString();
        }
        else if(holder instanceof Chest)
        {
            return "Chest:" + ((Chest)holder).getLocation().toString();
        }
        
        return null;
    }
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	void onPlayerInteract(PlayerInteractEvent event)
	{
	    Action action = event.getAction();
	    Player player = event.getPlayer();
	    
	    if(action == Action.PHYSICAL && !player.isSneaking() && PhantomAdmin.instance.isInvisible(player))
	    {
	        event.setCancelled(true);
	        PlayerData data = PlayerData.FromPlayer(player);
	        if(!data.gotPlateInfo && event.getClickedBlock().getType() != Material.SOIL)
	        {
	            PhantomAdmin.sendMessage(player, TextMode.Warn, Messages.NoTouchPlateWhileInvisible);
	            data.gotPlateInfo = true;
	        }
	        
	        return;
	    }
	    
		if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
		{
    		if(player.getGameMode() != GameMode.CREATIVE) return;
    		if(player.isSneaking()) return;
            if(player.getInventory().getItemInMainHand().getType() != Material.ENDER_PEARL) return;
    		
    		if(!player.hasPermission("phantomadmin.teleport")) return;
    		
    		event.setCancelled(true);
    		Block destinationBlock = player.getLocation().getBlock();
    		if(action == Action.RIGHT_CLICK_AIR)
    		{
    		    List<MetadataValue> values = player.getMetadata("lastTeleSource");
    	        if(values.size() >= 1)
    	        {
    	            Long now = System.currentTimeMillis();
                    Long then = (Long)(values.get(0).value());
    	            if(now - then < 500) return;
    	        }
    	        
    		    int maxDistance = 50;
    		    if(player.isSprinting()) maxDistance = 100;
    		    
    		    BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
                while (iterator.hasNext())
                {
                    Block nextBlock = iterator.next();
                    if(nextBlock.getType().isSolid()) break;
                    destinationBlock = nextBlock;
                }
    		}
    		
    		else if(action == Action.RIGHT_CLICK_BLOCK)
            {
    		    player.setMetadata("lastTeleSource", new FixedMetadataValue(PhantomAdmin.instance, new Long(System.currentTimeMillis())));
                
    		    int maxDistance = 100;
                
                Location loc = player.getEyeLocation();
                Location bloc = event.getClickedBlock().getLocation();
                loc.add(bloc.getBlockX() - loc.getBlockX(), bloc.getBlockY() - loc.getBlockY(), bloc.getBlockZ() - loc.getBlockZ());
                BlockIterator iterator = new BlockIterator(loc, 0, maxDistance);
    		    Block nextBlock = event.getClickedBlock();
    		    while(nextBlock != null)
                {
                    destinationBlock = nextBlock;
                    if(!destinationBlock.getType().isSolid()) break;
                    if(!iterator.hasNext()) break;
                    nextBlock = iterator.next();
                }
            }
    
    		Block underBlock = destinationBlock.getRelative(BlockFace.DOWN);
            if(underBlock.getType().isSolid())
            {
                Block overBlock = destinationBlock.getRelative(BlockFace.UP);
                if(!overBlock.getType().isSolid())
                {
                    destinationBlock = overBlock;
                }
            }
            
            boolean wasFlying = player.isFlying();
            Vector previousVelocity = player.getVelocity();
            
            float pitch = player.getLocation().getPitch();
            float yaw = player.getLocation().getYaw();
            Location destination = destinationBlock.getRelative(BlockFace.DOWN).getLocation();
            if(destination.getBlockY() < -2) destination.setY(-2);
            destination.setYaw(yaw);
            destination.setPitch(pitch);
            destination.add(.5, 0, .5);
            player.teleport(destination, TeleportCause.PLUGIN);
            
            if(!wasFlying)
            {
                if(!destination.getBlock().getType().isSolid())
                {
                    player.setFlying(true);
                }
            }
            else
            {
                player.setFlying(true);
            }
            
            player.setVelocity(previousVelocity);
            
            player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, .75f, 1f);
		}
	}
}
