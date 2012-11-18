/*
    ExtraHardMode Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.ExtraHardMode;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

//handles events related to entities
class EntityEventHandler implements Listener
{
	public EntityEventHandler()
	{
	}
	
	//when there's an explosion...
	@EventHandler(priority = EventPriority.NORMAL)
	public void onExplosion(EntityExplodeEvent event)
	{
		World world = event.getLocation().getWorld();
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		Entity entity = event.getEntity();
		
		//FEATURE: bigger TNT booms, all explosions have 100% block yield
		if(ExtraHardMode.instance.config_betterTNT)
		{
			event.setYield(1);
			
			if(entity != null && entity.getType() == EntityType.PRIMED_TNT)
			{
				event.setCancelled(true);
				entity.getWorld().createExplosion(entity.getLocation(), 8F);
			}
		}
		
		//FEATURE: in hardened stone mode, TNT only softens stone to cobble
		if(ExtraHardMode.instance.config_superHardStone)
		{
			List<Block> blocks = event.blockList();
			for(int i = 0; i < blocks.size(); i++)
			{
				Block block = blocks.get(i);
				if(block.getType() == Material.STONE)
				{
					block.setType(Material.COBBLESTONE);
					blocks.remove(i--);					
				}
				
				//FEATURE: more falling blocks
				ExtraHardMode.physicsCheck(block, 0, true);					
			}
		}
		
		//FEATURE: more powerful ghast fireballs
		if(entity != null && entity instanceof Fireball)
		{
			event.setCancelled(true);
			entity.getWorld().createExplosion(entity.getLocation(), 4F, true);  //same as vanilla TNT, plus fire
		}
		
		//FEATURE: bigger creeper explosions (for more-frequent cave-ins)
		if(entity != null && entity instanceof Creeper)
		{
			event.setCancelled(true);
			entity.getWorld().createExplosion(entity.getLocation(), 3F, false);  //same as vanilla TNT
		}
	}
	
	//when a creature spawns...
	@EventHandler(priority = EventPriority.LOW)
	public void onEntitySpawn(CreatureSpawnEvent event)
	{
		Location location = event.getLocation();
		World world = location.getWorld();
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//avoid infinite loops
		if(event.getSpawnReason() == SpawnReason.CUSTOM) return;
		
		LivingEntity entity = event.getEntity();
		
		EntityType entityType = entity.getType();
		
		//FEATURE: inhibited monster grinders/farms
		if(ExtraHardMode.instance.config_inhibitMonsterGrinders)
		{
			SpawnReason reason = event.getSpawnReason();
			
			//spawners and spawn eggs always spawn a monster, but the monster doesn't drop any loot
			if(reason == SpawnReason.SPAWNER && (ExtraHardMode.instance.config_bonusNetherBlazeSpawnPercent > 0 || !(entity instanceof Blaze)))
			{
				EntityEventHandler.markLootLess(entity);
			}
			
			//otherwise, consider environment to stop monsters from spawning in non-natural places
			else if(reason == SpawnReason.NATURAL || reason == SpawnReason.VILLAGE_INVASION)
			{
				Environment environment = location.getWorld().getEnvironment();
				
				Material underBlockType = location.getBlock().getRelative(BlockFace.DOWN).getType();
				if(environment == Environment.NORMAL)
				{
					if(	underBlockType != Material.GRASS && 
						underBlockType != Material.STONE && 
						underBlockType != Material.SAND && 
						underBlockType != Material.GRAVEL && 
						underBlockType != Material.MOSSY_COBBLESTONE &&
						underBlockType != Material.COBBLESTONE)
					{
							event.setCancelled(true);
							return;
					}
				}
				else if(environment == Environment.NETHER)
				{
					if(	underBlockType != Material.NETHERRACK && 
						underBlockType != Material.NETHER_BRICK && 
						underBlockType != Material.SOUL_SAND &&
						underBlockType != Material.AIR )  //ghasts
					{
							event.setCancelled(true);
							return;
					}
				}
				else
				{
					if(	underBlockType != Material.ENDER_STONE && 
						underBlockType != Material.OBSIDIAN && 
						underBlockType != Material.AIR )  //ender dragon
					{
							event.setCancelled(true);
							return;
					}
				}
			}
		}
		
		//FEATURE: charged creeper spawns
		if(entityType == EntityType.CREEPER)
		{
			if(ExtraHardMode.random(ExtraHardMode.instance.config_chargedCreeperSpawnPercent))
			{
				((Creeper)entity).setPowered(true);
			}				
		}
		
		//FEATURE: more spiders underground
		if(entityType == EntityType.ZOMBIE && world.getEnvironment() == Environment.NORMAL && location.getBlockY() < world.getSeaLevel() - 5)
		{
			if(ExtraHardMode.random(ExtraHardMode.instance.config_bonusUndergroundSpiderSpawnPercent))
			{
				event.setCancelled(true);
				entityType = EntityType.SPIDER;
				world.spawnEntity(location, entityType);
			}
		}
		
		//FEATURE: blazes near bedrock
		else if(entityType == EntityType.SKELETON && world.getEnvironment() == Environment.NORMAL && location.getBlockY() < 20)
		{
			if(ExtraHardMode.random(ExtraHardMode.instance.config_nearBedrockBlazeSpawnPercent))
			{
				event.setCancelled(true);
				entityType = EntityType.BLAZE;
				world.spawnEntity(location, entityType);
			}
		}
		
		//FEATURE: more blazes
		else if(entityType == EntityType.PIG_ZOMBIE)
		{
			if(ExtraHardMode.random(ExtraHardMode.instance.config_bonusNetherBlazeSpawnPercent))
			{
				event.setCancelled(true);
				entityType = EntityType.BLAZE;
				
				//FEATURE: magma cubes spawn with blazes
				if(ExtraHardMode.random(ExtraHardMode.instance.config_flameSlimesSpawnWithNetherBlazePercent))
				{
					MagmaCube cube = (MagmaCube)(world.spawnEntity(location, EntityType.MAGMA_CUBE));
					cube.setSize(1);
				}
				world.spawnEntity(location, entityType);
			}
		}
				
		//FEATURE: extra monster spawns underground
		if(ExtraHardMode.instance.config_moreMonstersMaxY > 0)
		{
			if(	world.getEnvironment() == Environment.NORMAL &&
				event.getLocation().getBlockY() < ExtraHardMode.instance.config_moreMonstersMaxY && 
				entity instanceof Monster)
			{
				for(int i = 1; i < ExtraHardMode.instance.config_moreMonstersMultiplier; i++)
				{
					Entity newEntity = world.spawnEntity(event.getLocation(), entityType);
					if(EntityEventHandler.isLootLess(entity))
					{
						EntityEventHandler.markLootLess((LivingEntity)newEntity);
					}
				}
			}
		}
		
		//FEATURE: always-angry pig zombies
		if(ExtraHardMode.instance.config_alwaysAngryPigZombies)
		{
			if(entity instanceof PigZombie)
			{
				PigZombie pigZombie = (PigZombie)entity;
				pigZombie.setAnger(Integer.MAX_VALUE);
			}
		}
	}
	
	//when an entity shoots a bow...
	@EventHandler
	public void onShootProjectile(ProjectileLaunchEvent event)
	{
		Location location = event.getEntity().getLocation();
		World world = location.getWorld();
		EntityType entityType = event.getEntityType();
		
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		if(event.getEntity() == null) return;
		
		//FEATURE: skeletons sometimes release silverfish to attack their targets
		if(entityType != EntityType.ARROW) return;
		
		Arrow arrow = (Arrow)event.getEntity();
		
		LivingEntity shooter = arrow.getShooter();
		if(shooter != null && shooter.getType() == EntityType.SKELETON && ExtraHardMode.random(ExtraHardMode.instance.config_skeletonsReleaseSilverfishPercent))
		{
			Skeleton skeleton = (Skeleton)shooter;
			
			//cancel arrow fire
			event.setCancelled(true);
			
			//replace with silverfish, quarter velocity of arrow, wants to attack same target as skeleton
			Creature silverFish = (Creature)world.spawnEntity(skeleton.getLocation().add(0, 1.5, 0), EntityType.SILVERFISH);
			silverFish.setVelocity(arrow.getVelocity().multiply(.25));
			silverFish.setTarget(skeleton.getTarget());
			EntityEventHandler.markLootLess(silverFish);  //this silverfish doesn't drop loot
		}
	}

	//when a chunk loads...
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event)
	{
		Chunk chunk = event.getChunk();
		World world = chunk.getWorld();
		
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//FEATURE: always-angry pig zombies
		if(ExtraHardMode.instance.config_alwaysAngryPigZombies)
		{
			Entity [] entities = chunk.getEntities();		
			for(int i = 0; i < entities.length; i++)
			{			
				if(entities[i] instanceof PigZombie)
				{
					PigZombie pigZombie = (PigZombie)entities[i];
					pigZombie.setAnger(Integer.MAX_VALUE);
				}
			}
		}		
	}		
	
	//when an entity dies...
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event)
	{
		LivingEntity entity = event.getEntity();
		World world = entity.getWorld();
		
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//FEATURE: some portion of player inventory is permanently lost on death
		if(entity instanceof Player)
		{
			Player player = (Player)entity;
			if(!player.hasPermission("extrahardmode.bypass"))
			{
				List<ItemStack> drops = event.getDrops();
				int numberOfStacksToRemove = (int)(drops.size() * (ExtraHardMode.instance.config_playerDeathItemStacksForfeitPercent / 100f));
				for(int i = 0; i < numberOfStacksToRemove && drops.size() > 0; i++)
				{
					int indexOfStackToRemove = ExtraHardMode.randomNumberGenerator.nextInt(drops.size());
					drops.remove(indexOfStackToRemove);
				}
			}
		}
		
		//FEATURE: zombies may reanimate if not on fire when they die
		if(ExtraHardMode.instance.config_zombiesReanimatePercent > 0)
		{
			if(entity.getType() == EntityType.ZOMBIE)
			{
				if(entity.getFireTicks() < 1 && ExtraHardMode.random(ExtraHardMode.instance.config_zombiesReanimatePercent))
				{
					Player playerTarget = null;
					Zombie zombie = (Zombie)entity;
					Entity target = zombie.getTarget();
					if(target instanceof Player)
					{
						playerTarget = (Player)target;
					}

					RespawnZombieTask task = new RespawnZombieTask(entity.getLocation(), playerTarget);
					int respawnSeconds = ExtraHardMode.randomNumberGenerator.nextInt(6) + 3;  //3-8 seconds
					ExtraHardMode.instance.getServer().getScheduler().scheduleSyncDelayedTask(ExtraHardMode.instance, task, 20L * respawnSeconds);  ///20L ~ 1 second
				}
			}
		}
		
		//FEATURE: pig zombies drop nether wart when slain in nether fortresses
		if(ExtraHardMode.instance.config_fortressPigsDropWart && world.getEnvironment() == Environment.NETHER && entity instanceof PigZombie)
		{
			Block underBlock = entity.getLocation().getBlock().getRelative(BlockFace.DOWN);
			if(underBlock.getType() == Material.NETHER_BRICK)
			{
				event.getDrops().add(new ItemStack(Material.NETHER_STALK));
			}
		}
		
		//FEATURE: nether blazes drop extra loot (glowstone and gunpowder)
		if(ExtraHardMode.instance.config_blazesDropBonusLoot && world.getEnvironment() == Environment.NETHER && entity instanceof Blaze)
		{
			//50% chance of each
			if(ExtraHardMode.randomNumberGenerator.nextInt(2) == 0)
			{
				event.getDrops().add(new ItemStack(Material.SULPHUR, 2));
			}
			else
			{
				event.getDrops().add(new ItemStack(Material.GLOWSTONE_DUST, 2));
			}
		}
		
		
		//FEATURE: monsters which take environmental damage or spawn from spawners don't drop loot and exp (monster grinder inhibitor)
		if(ExtraHardMode.instance.config_inhibitMonsterGrinders && entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.SQUID)
		{
			boolean noLoot = false;
			
			if(EntityEventHandler.isLootLess(entity))
			{
				noLoot = true;				
			}
			
			//also no loot for monsters which die standing in water
			else
			{
				Block block = entity.getLocation().getBlock();
				Block underBlock = block.getRelative(BlockFace.DOWN);
				Block [] adjacentBlocks = new Block []
				{
					block,
					block.getRelative(BlockFace.EAST),
					block.getRelative(BlockFace.WEST),
					block.getRelative(BlockFace.NORTH),
					block.getRelative(BlockFace.SOUTH),
					block.getRelative(BlockFace.NORTH_EAST),
					block.getRelative(BlockFace.SOUTH_EAST),
					block.getRelative(BlockFace.NORTH_WEST),
					block.getRelative(BlockFace.SOUTH_WEST),
					underBlock,
					underBlock.getRelative(BlockFace.EAST),
					underBlock.getRelative(BlockFace.WEST),
					underBlock.getRelative(BlockFace.NORTH),
					underBlock.getRelative(BlockFace.SOUTH),
					underBlock.getRelative(BlockFace.NORTH_EAST),
					underBlock.getRelative(BlockFace.SOUTH_EAST),
					underBlock.getRelative(BlockFace.NORTH_WEST),
					underBlock.getRelative(BlockFace.SOUTH_WEST)
				};
				
				for(int i = 0; i < adjacentBlocks.length; i++)
				{
					block = adjacentBlocks[i];
					if(block.getType() == Material.WATER || block.getType() == Material.STATIONARY_WATER)
					{
						noLoot = true;
						break;
					}
				}
			}
			
			if(noLoot)
			{
				event.setDroppedExp(0);
				event.getDrops().clear();
			}
		}
		
		//FEATURE: ghasts deflect arrows and drop extra loot and exp
		if(ExtraHardMode.instance.config_ghastsDeflectArrows)
		{
			if(entity instanceof Ghast)
			{
				event.setDroppedExp(event.getDroppedExp() * 10);
				List<ItemStack> itemDrops = event.getDrops();
				for(int i = 0; i < itemDrops.size(); i++)
				{
					ItemStack itemDrop = itemDrops.get(i);
					itemDrop.setAmount(itemDrop.getAmount() * 10);
				}
			}
		}	

		//FEATURE: blazes explode on death in normal world
		if(ExtraHardMode.instance.config_blazesExplodeOnDeath && entity instanceof Blaze && world.getEnvironment() == Environment.NORMAL)
		{
			//create explosion
			world.createExplosion(entity.getLocation(), 2F, true);  //equal to a TNT blast, sets fires
			
			//fire a fireball straight up in normal worlds
			Fireball fireball = (Fireball) world.spawnEntity(entity.getLocation(), EntityType.FIREBALL);
			fireball.setDirection(new Vector(0, 10, 0));
			fireball.setYield(1);
		}
		
		//FEATURE: nether blazes may multiply on death
		if(ExtraHardMode.instance.config_netherBlazesSplitOnDeathPercent > 0 && world.getEnvironment() == Environment.NETHER && entity instanceof Blaze)
		{
			if(ExtraHardMode.random(ExtraHardMode.instance.config_netherBlazesSplitOnDeathPercent))
			{
				Entity firstNewBlaze = world.spawnEntity(entity.getLocation(), EntityType.BLAZE);
				firstNewBlaze.setVelocity(new Vector(1, 0, 1));
				
				Entity secondNewBlaze = world.spawnEntity(entity.getLocation(), EntityType.BLAZE);
				secondNewBlaze.setVelocity(new Vector(-1, 0, -1));
				
				//if this blaze was marked lootless, mark the new blazes the same
				if(EntityEventHandler.isLootLess((LivingEntity)entity))
				{
					EntityEventHandler.markLootLess((LivingEntity)firstNewBlaze);
					EntityEventHandler.markLootLess((LivingEntity)secondNewBlaze);
				}
			}
		}
		
		//FEATURE: spiders drop web on death
		if(ExtraHardMode.instance.config_spidersDropWebOnDeath)
		{
			if(entity instanceof Spider)
			{
				//random web placement
				long serverTime = world.getFullTime();
				int random1 = (int)(serverTime + entity.getLocation().getBlockZ()) % 9;
				int random2 = (int)(serverTime + entity.getLocation().getBlockX()) % 9;
				
				Location [] locations = new Location [4];
				
				locations[0] = entity.getLocation().add(random1, 0, random2);
				locations[1] = entity.getLocation().add(-random2, 0, random1 / 2);
				locations[2] = entity.getLocation().add(-random1 / 2, 0, -random2);
				locations[3] = entity.getLocation().add(random1 / 2, 0, -random2 / 2);
				
				ArrayList<Block> changedBlocks = new ArrayList<Block>();
				for(int i = 0; i < locations.length; i++)
				{
					Location location = locations[i];
					Block block = location.getBlock();
					
					//don't replace anything solid with web
					if(block.getType() != Material.AIR) continue;
					
					//only place web on the ground, not hanging up in the air
					do
					{
						block = block.getRelative(BlockFace.DOWN);
					}while(block.getType() == Material.AIR);
					
					//don't place web over fluids or stack webs
					if(!block.isLiquid() && block.getType() != Material.WEB)
					{
						block = block.getRelative(BlockFace.UP);
						
						//don't place next to cactus, because it will break the cactus
						Block [] adjacentBlocks = new Block []
						{
							block.getRelative(BlockFace.EAST), 
							block.getRelative(BlockFace.WEST),
							block.getRelative(BlockFace.NORTH),
							block.getRelative(BlockFace.SOUTH)
						};
						
						boolean nextToCactus = false;
						for(int j = 0; j < adjacentBlocks.length; j++)
						{
							if(adjacentBlocks[j].getType() == Material.CACTUS) 
							{
								nextToCactus = true;
								break;
							}
						}
							
						if(!nextToCactus)
						{
							block.setType(Material.WEB);
							changedBlocks.add(block);
						}
					}
				}
				
				//any webs placed above sea level will be automatically cleaned up after a short time
				if(entity.getLocation().getBlockY() >= entity.getLocation().getWorld().getSeaLevel() - 5)
				{
					WebCleanupTask task = new WebCleanupTask(changedBlocks);
					ExtraHardMode.instance.getServer().getScheduler().scheduleSyncDelayedTask(ExtraHardMode.instance, task, 20L * 30);
				}
			}
		}
	}
	
	//when an entity is damaged
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityDamage (EntityDamageEvent event)
	{
		Entity entity = event.getEntity();
		EntityType entityType = entity.getType();
		World world = entity.getWorld();
		
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//is this an entity damaged by entity event?
		EntityDamageByEntityEvent subEvent = null;		
		if(event instanceof EntityDamageByEntityEvent)
		{
			subEvent = (EntityDamageByEntityEvent)event;
		}
		
		//FEATURE: zombies can apply a debilitating effects
		if(ExtraHardMode.instance.config_zombiesDebilitatePlayers)
		{
			if(subEvent != null && subEvent.getDamager() instanceof Zombie)
			{
				if(entity instanceof Player)
				{
					Player player = (Player)entity;
					if(!player.hasPermission("extrahardmode.bypass"))
					{
						player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 10, 3));
					}
				}
			}
		}
		
		//FEATURE: magma cubes become blazes when they take damage
		if(entityType == EntityType.MAGMA_CUBE && ExtraHardMode.instance.config_magmaCubesBecomeBlazesOnDamage && !entity.isDead())
		{
			entity.remove();  //remove magma cube
			entity.getWorld().spawnEntity(entity.getLocation().add(0, 2, 0), EntityType.BLAZE);  //replace with blaze
			entity.getWorld().createExplosion(entity.getLocation(), 2F, true); //fiery explosion for effect				
		}
		
		//FEATURE: arrows pass through skeletons
		if(entityType == EntityType.SKELETON && subEvent != null && ExtraHardMode.instance.config_skeletonsDeflectArrowsPercent > 0)
		{
			Entity damageSource = subEvent.getDamager();
			
			//only arrows
			if(damageSource instanceof Arrow)
			{
				Arrow arrow = (Arrow)damageSource;
				
				//percent chance
				if(ExtraHardMode.random(ExtraHardMode.instance.config_skeletonsDeflectArrowsPercent))
				{
				
					//cancel the damage
					event.setCancelled(true);
					
					//teleport the arrow a single block farther along its flight path
					//note that .6 and 12 were the unexplained recommended values for speed and spread, reflectively, in the bukkit wiki
					arrow.remove();
					world.spawnArrow(arrow.getLocation().add((arrow.getVelocity().normalize()).multiply(2)), arrow.getVelocity(), .6f, 12f);
				}
			}
		}
		
		//FEATURE: extra damage and effects from environmental damage
		if(ExtraHardMode.instance.config_enhancedEnvironmentalDamage)
		{
			Player player = null;
			if(entity instanceof Player)
			{
				player = (Player)entity;
			}
			
			if(player != null && !player.hasPermission("extrahardmode.bypass"))
			{
				DamageCause cause = event.getCause();
				
				if(event.getDamage() > 2 && (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION))
				{
					player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20 * 15, 3));
				}
				else if(cause == DamageCause.FALL)
				{
					player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * event.getDamage(), 4));
					event.setDamage(event.getDamage() * 2);
				}
				else if(cause == DamageCause.SUFFOCATION)
				{
					event.setDamage(event.getDamage() * 5);
				}
				else if(cause == DamageCause.LAVA)
				{
					event.setDamage(event.getDamage() * 2);					
				}				
				else if(cause == DamageCause.FIRE_TICK)
				{
					player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 1, 1));
				}
			}
		}	
		
		//FEATURE: skeletons can knock back
		if(ExtraHardMode.instance.config_skeletonsKnockBackPercent > 0)
		{
			if(subEvent != null)
			{
				if(subEvent.getDamager() instanceof Arrow)
				{
					Arrow arrow = (Arrow)(subEvent.getDamager());
					if(arrow.getShooter() != null && arrow.getShooter() instanceof Skeleton)
					{
						if(ExtraHardMode.random(ExtraHardMode.instance.config_skeletonsKnockBackPercent))
						{
							//cut damage in half
							event.setDamage(event.getDamage() / 2);
							
							//knock back target with half the arrow's velocity
							entity.setVelocity(arrow.getVelocity());
						}
					}
				}
			}
		}
		
		//FEATURE: monsters trapped in webbing break out of the webbing when hit
		if(entity instanceof Monster)
		{
			this.clearWebbing(entity);
		}
		
		//FEATURE: blazes drop fire on hit
		if(ExtraHardMode.instance.config_blazesDropFireOnDamage)
		{
			if(entityType == EntityType.BLAZE)
			{
				Blaze blaze = (Blaze)entity;
				
				if(blaze.getHealth() > blaze.getMaxHealth() / 2)
				{
				
					Block block = entity.getLocation().getBlock();
					
					Block underBlock = block.getRelative(BlockFace.DOWN);
					while(underBlock.getType() == Material.AIR)
						underBlock = underBlock.getRelative(BlockFace.DOWN);
					
					block = underBlock.getRelative(BlockFace.UP); 
					if(block.getType() == Material.AIR && underBlock.getType() != Material.AIR && !underBlock.isLiquid())
					{
						block.setType(Material.FIRE);
					}
				}
			}
		}
		
		//FEATURE: charged creepers explode on hit
		if(ExtraHardMode.instance.config_chargedCreepersExplodeOnHit)
		{
			if(entityType == EntityType.CREEPER && !entity.isDead())
			{
				Creeper creeper = (Creeper)entity;
				if(creeper.isPowered())
				{
					entity.remove();
					world.createExplosion(entity.getLocation(), 4F);  //equal to a TNT blast
				}
			}
		}
		
		//FEATURE: ghasts deflect arrows and drop extra loot
		if(ExtraHardMode.instance.config_ghastsDeflectArrows)
		{
			//only ghasts, and only if damaged by another entity (as opposed to environmental damage)
			if(entity instanceof Ghast && event instanceof EntityDamageByEntityEvent)
			{
				Entity damageSource = subEvent.getDamager();
				
				//only arrows
				if(damageSource instanceof Arrow)
				{
					//who shot it?
					Arrow arrow = (Arrow)damageSource;
					if(arrow.getShooter() != null && arrow.getShooter() instanceof Player)
					{
						//check permissions when it's shot by a player
						Player player = (Player)arrow.getShooter();
						event.setCancelled(!player.hasPermission("extrahardmode.bypass"));
					}
					else
					{
						//otherwise always deflect
						event.setCancelled(true);
						return;
					}
				}
			}				
		}
		
		//FEATURE: monsters which take environmental damage don't drop loot or experience (monster grinder inhibitor)
		if(ExtraHardMode.instance.config_inhibitMonsterGrinders && entity instanceof LivingEntity)
		{
			DamageCause damageCause = event.getCause();
			if(damageCause != DamageCause.ENTITY_ATTACK && damageCause != DamageCause.PROJECTILE)
			{
				EntityEventHandler.addEnvironmentalDamage((LivingEntity)entity, event.getDamage());
			}
		}
	}
	
	//when a sheep regrows its wool...
	@EventHandler
	public void onSheepRegrowWool(SheepRegrowWoolEvent event)
	{
		World world = event.getEntity().getWorld();
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//FEATURE: sheep are all white, and may be dyed only temporarily
		if(ExtraHardMode.instance.config_sheepRegrowWhiteWool)
		{
			Sheep sheep = event.getEntity();
			sheep.setColor(DyeColor.WHITE);
		}
	}
	
	//when an entity (not a player) teleports...
	@EventHandler
	public void onEntityTeleport(EntityTeleportEvent event)
	{
		Entity entity = event.getEntity();
		World world = entity.getWorld();
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		if(world.getEnvironment() != Environment.NORMAL) return;
		
		if(entity instanceof Enderman && ExtraHardMode.instance.config_improvedEndermanTeleportation)
		{
			Enderman enderman = (Enderman)entity;
			
			//ignore endermen which aren't fighting players
			if(enderman.getTarget() == null || !(enderman.getTarget() instanceof Player)) return;
			
			Player player = (Player)enderman.getTarget();
			
			//half the time, teleport the player instead
			if(ExtraHardMode.random(50))
			{		
				event.setCancelled(true);
				int distanceSquared = (int)player.getLocation().distanceSquared(enderman.getLocation());
				
				//play sound at old location
				world.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
				Block destinationBlock;
				
				//if the player is far away
				if(distanceSquared > 75)
				{
					//have the enderman swap places with the player
					destinationBlock = enderman.getLocation().getBlock();
					enderman.teleport(player.getLocation());
				}
				
				//otherwise if the player is close
				else
				{
					//teleport the player to the enderman's destination
					destinationBlock = event.getTo().getBlock();				
				}
				
				while(destinationBlock.getType() != Material.AIR || destinationBlock.getRelative(BlockFace.UP).getType() != Material.AIR)
				{
					destinationBlock = destinationBlock.getRelative(BlockFace.UP);
				}
				
				player.teleport(destinationBlock.getLocation(), TeleportCause.ENDER_PEARL);
				
				//play sound at new location
				world.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
			}
		}
	}
	
	//when an entity targets something (as in to attack it)
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event)
	{
		Entity entity = event.getEntity();
		World world = entity.getWorld();
		if(!ExtraHardMode.instance.config_enabled_worlds.contains(world)) return;
		
		//FEATURE: a monster which gains a target breaks out of any webbing it might have been trapped within
		if(entity instanceof Monster)
		{
			this.clearWebbing(entity);
		}
	}
	
	//marks an entity so that the plugin can remember not to drop loot or experience if it's killed
	static void markLootLess(LivingEntity entity)
	{		
		entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(ExtraHardMode.instance, entity.getMaxHealth()));
	}
	
	//tracks total environmental damage done to an entity
	static void addEnvironmentalDamage(LivingEntity entity, int damage)
	{
		if(!entity.hasMetadata("extrahard_environmentalDamage"))
		{
			entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(ExtraHardMode.instance, damage));
		}
		else
		{
			int currentTotalDamage = entity.getMetadata("extrahard_environmentalDamage").get(0).asInt();			
			entity.setMetadata("extrahard_environmentalDamage", new FixedMetadataValue(ExtraHardMode.instance, currentTotalDamage + damage));
		}
	}
	
	//checks whether an entity should drop items when it dies
	static boolean isLootLess(LivingEntity entity)
	{
		if(entity instanceof Creature && entity.hasMetadata("extrahard_environmentalDamage"))
		{
			int totalDamage = entity.getMetadata("extrahard_environmentalDamage").get(0).asInt();
			return (totalDamage > entity.getMaxHealth() / 2);
		}
		
		return false;
	}
	
	//clears any webbing which may be trapping this entity (assumes two-block-tall entity)
	void clearWebbing(Entity entity)
	{
		Block feetBlock = entity.getLocation().getBlock();
		Block headBlock = feetBlock.getRelative(BlockFace.UP);
		
		Block [] blocks = {feetBlock, headBlock};			
		for(int i = 0; i < blocks.length; i++)
		{
			Block block = blocks[i];
			if(block.getType() == Material.WEB)
			{
				block.setType(Material.AIR);
			}
		}
	}
}