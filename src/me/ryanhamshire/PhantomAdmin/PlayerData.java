//Copyright 2015 Ryan Hamshire

package me.ryanhamshire.PhantomAdmin;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

class PlayerData 
{
    private final static String METADATA_TAG = "PA_PlayerData";
    
    boolean gotPlateInfo = false;
    boolean gotItemPickupInfo = false;
    boolean gotItemDropInfo = false;
    boolean chatLocked = PhantomAdmin.instance.config_invisibleByDefault;
    boolean invisible = PhantomAdmin.instance.config_invisibleByDefault;

    String loginMessage = null;
    
    static PlayerData FromPlayer(Player player)
    {
        List<MetadataValue> data = player.getMetadata(METADATA_TAG);
        if(data == null || data.isEmpty())
        {
            return new PlayerData(player);
        }
        else
        {
            try
            {
                PlayerData playerData = (PlayerData)(data.get(0).value());
                return playerData;
            }
            catch(Exception e)
            {
                return new PlayerData(player);
            }
        }
    }
    
    private PlayerData(Player player)
    {
        player.setMetadata(METADATA_TAG, new FixedMetadataValue(PhantomAdmin.instance, this));
    }
}