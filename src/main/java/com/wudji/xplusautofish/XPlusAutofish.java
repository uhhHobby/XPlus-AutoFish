package com.wudji.xplusautofish;


import com.wudji.xplusautofish.mointor.FishMonitorMP;
import com.wudji.xplusautofish.mointor.FishMonitorMPMotion;
import com.wudji.xplusautofish.mointor.FishMonitorMPSound;
import com.wudji.xplusautofish.scheduler.ActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.FishingRodItem;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XPlusAutofish {
    private Minecraft client;
    private ForgeModXPlusAutofish modAutofish;
    private FishMonitorMP fishMonitorMP;

    private boolean hookExists = false;
    private long hookRemovedAt = 0L;

    public long timeMillis = 0L;

    public XPlusAutofish(ForgeModXPlusAutofish modAutofish) {
        this.modAutofish = modAutofish;
        this.client = Minecraft.getInstance();
        setDetection();

        //Initiate the repeating action for persistent mode casting
        modAutofish.getScheduler().scheduleRepeatingAction(10000, () -> {
            if(!modAutofish.getConfig().isPersistentMode()) return;
            if(!isHoldingFishingRod()) return;
            if(hookExists) return;
            if(modAutofish.getScheduler().isRecastQueued()) return;

            useRod();
        });
    }

    public void tick(Minecraft client) {

        if (client.level != null && client.player != null && modAutofish.getConfig().isAutofishEnabled()) {

            timeMillis = Util.getMillis(); //update current working time for this tick

            if (isHoldingFishingRod()) {
                if (client.player.fishing != null) {
                    hookExists = true;
                    //MP catch listener
                    if (shouldUseMPDetection()) {//multiplayer only, send tick event to monitor
                        fishMonitorMP.hookTick(this, client, client.player.fishing);
                    }
                } else {
                    removeHook();
                }
            } else { //not holding fishing rod
                removeHook();
            }
        }
    }

    /**
     * Callback from mixin for the catchingFish method of the EntityFishHook
     * for singleplayer detection only
     */
    public void tickFishingLogic(Entity owner, int ticksCatchable) {
        if (modAutofish.getConfig().isAutofishEnabled() && !shouldUseMPDetection()) {
            //null checks for sanity
            if (client.player != null && client.player.fishing != null) {
                //hook is catchable and player is correct
                if (ticksCatchable > 0 && owner.getUUID().compareTo(client.player.getUUID()) == 0) {
                    catchFish();
                }
            }
        }
    }

    /**
     * Callback from mixin when sound and motion packets are received
     * For multiplayer detection only
     */
    public void handlePacket(Packet<?> packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (shouldUseMPDetection()) {
                fishMonitorMP.handlePacket(this, packet, client);
            }
        }
    }

    /**
     * Callback from mixin when chat packets are received
     * For multiplayer detection only
     */
    public void handleChat(ClientboundChatPacket packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (!client.isLocalServer()) {
                if (isHoldingFishingRod()) {
                    //check that either the hook exists, or it was just removed
                    //this prevents false casts if we are holding a rod but not fishing
                    if (hookExists || (timeMillis - hookRemovedAt < 2000)) {
                        //make sure there is actually something there in the regex field
                        if (org.apache.commons.lang3.StringUtils.deleteWhitespace(modAutofish.getConfig().getClearLagRegex()).isEmpty())
                            return;
                        //check if it matches
                        Matcher matcher = Pattern.compile(modAutofish.getConfig().getClearLagRegex(), Pattern.CASE_INSENSITIVE).matcher(StringUtil.stripColor(packet.getMessage().getString()));
                        if (matcher.find()) {
                            queueRecast();
                        }
                    }
                }
            }
        }
    }

    public void catchFish() {
        if(!modAutofish.getScheduler().isRecastQueued()) { //prevents double reels
            //queue actions
            queueRodSwitch();
            queueRecast();

            //reel in
            useRod();
        }
    }

    public void queueRecast() {
        modAutofish.getScheduler().scheduleAction(ActionType.RECAST, modAutofish.getConfig().getRecastDelay(), () -> {
            //State checks to ensure we can still fish once this runs
            if(hookExists) return;
            if(!isHoldingFishingRod()) return;
            if(modAutofish.getConfig().isNoBreak() && getHeldItem().getDamageValue() >= 63) return;

            useRod();
        });
    }

    private void queueRodSwitch(){
        modAutofish.getScheduler().scheduleAction(ActionType.ROD_SWITCH, modAutofish.getConfig().getRecastDelay() - 500, () -> {
            if(!modAutofish.getConfig().isMultiRod()) return;

            switchToFirstRod(client.player);
        });
    }

    /**
     * Call this when the hook disappears
     */
    private void removeHook() {
        if (hookExists) {
            hookExists = false;
            hookRemovedAt = timeMillis;
            fishMonitorMP.handleHookRemoved();
        }
    }

    public void switchToFirstRod(LocalPlayer player) {
        if(player != null) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack slot = inventory.items.get(i);
                if (slot.getItem() == Items.FISHING_ROD) {
                    if (i < 9) { //hotbar only
                        if (modAutofish.getConfig().isNoBreak()) {
                            if (slot.getDamageValue() < 63) {
                                inventory.selected = i;
                                return;
                            }
                        } else {
                            inventory.selected = i;
                            return;
                        }
                    }
                }
            }
        }
    }

    public void useRod() {
        if(client.player != null && client.level != null) {
            InteractionHand hand = getCorrectHand();
            InteractionResult actionResult = null;
            if (client.gameMode != null) {
                actionResult = client.gameMode.useItem(client.player, client.level, hand);
            }
            if (actionResult != null && actionResult.consumesAction()) {
                if (actionResult.shouldSwing()) {
                    client.player.swing(hand);
                }
                client.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
        }
    }

    public boolean isHoldingFishingRod() {
        return isItemFishingRod(getHeldItem().getItem());
    }

    private InteractionHand getCorrectHand() {
        if (!modAutofish.getConfig().isMultiRod()) {
            if (client.player != null && isItemFishingRod(client.player.getOffhandItem().getItem()))
                return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private ItemStack getHeldItem() {
        if (!modAutofish.getConfig().isMultiRod()) {
            if (client.player != null && isItemFishingRod(client.player.getOffhandItem().getItem()))
                return client.player.getOffhandItem();
        }
        if (client.player != null) {
            return client.player.getMainHandItem();
        }
        return null;
    }

    private boolean isItemFishingRod(Item item) {
        return item == Items.FISHING_ROD || item instanceof FishingRodItem;
    }

    public void setDetection() {
        if (modAutofish.getConfig().isUseSoundDetection()) {
            fishMonitorMP = new FishMonitorMPSound();
        } else {
            fishMonitorMP = new FishMonitorMPMotion();
        }
    }

    private boolean shouldUseMPDetection(){
        if(modAutofish.getConfig().isForceMPDetection()) return true;
        return !client.isLocalServer();
    }
    
}
