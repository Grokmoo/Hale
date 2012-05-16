/*
 * Hale is highly moddable tactical RPG.
 * Copyright (C) 2011 Jared Stephen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package net.sf.hale.interfacelock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.sf.hale.Game;
import net.sf.hale.entity.Creature;
import net.sf.hale.entity.Entity;
import net.sf.hale.rules.CombatRunner;
import net.sf.hale.util.AreaUtil;
import net.sf.hale.util.Point;

/**
 * Class for moving entities around inside the current Area
 * @author Jared Stephen
 *
 */

public class MovementHandler {
	/**
	 * The available types of movement modes.  These only apply to movement outside of combat
	 * @author Jared
	 *
	 */
	
	public enum Mode {
		/** When not in combat, all party members follow along after the selected party member */
		Party,
		
		/** When not in combat, only the selected party member moves */
		Single;
	}
	
	private Mode mode;
	
	private List<Mover> moves;
	
	private boolean interrupted;
	
	/**
	 * Creates a new empty MovementHandler
	 */
	
	public MovementHandler() {
		moves = new ArrayList<Mover>();
		interrupted = false;
		this.mode = Mode.Party;
	}
	
	/**
	 * Updates the current time for this MovementHandler, also updating the time
	 * for all currently active moves and moving as needed
	 * @param curTime the current time in milliseconds
	 */
	
	public synchronized void update(long curTime) {
		Iterator<Mover> moveIter = moves.iterator();
		while (moveIter.hasNext()) {
			Mover move = moveIter.next();
			
			if (move.creature.isDead() && (!move.creature.isPlayerSelectable() || Game.isInTurnMode()) ) {
				move.finish();
				moveIter.remove();
			} else if (move.isReadyForMovement(curTime)) {
				if (move.lastIndex == 0) {
					move.finish();
					moveIter.remove();
				} else {
					move.performMovement(curTime);
				}
			}
		}
		
		if (interrupted) {
			clear();
			interrupted = false;
		}
	}
	
	/**
	 * Returns the current movement mode for moves
	 * @return the current movement mode
	 */
	
	public Mode getMovementMode() {
		return mode;
	}
	
	/**
	 * Sets the current movement mode to the specified mode
	 * @param mode the mode to set
	 */
	
	public void setMovementMode(Mode mode) {
		this.mode = mode;
	}
	
	/**
	 * Interrupts and immediately ends all movement
	 */
	
	public void interrupt() {
		interrupted = true;
	}
	
	/**
	 * Returns true if this movement handler is currently handling one or more
	 * moves
	 * @return whether this movement handler is currently handling one or more moves
	 */
	
	public boolean isLocked() {
		return moves.size() > 0;
	}
	
	/**
	 * Adds a movement path for the specified creature to be completed
	 * by this handler
	 * @param creature the creature that is moving
	 * @param path the path that the creature is moving
	 * @return the Move created for this movement
	 */
	
	public Mover addMove(Creature creature, List<Point> path, boolean provokeAoOs) {
		creature.setCurrentlyMoving(true);
		
		Mover mover = new Mover(creature, path, provokeAoOs);
		
		synchronized(this) {
			moves.add(mover);
		}
		
		mover.checkAoOsAndAnimate();
		
		return mover;
	}
	
	/**
	 * Removes all current moves from this MovementHandler
	 */
	
	public void clear() {
		// set all creatures to not moving so any overlaping creatures
		// can find empty tiles
		for (Mover mover : moves) {
			mover.creature.setCurrentlyMoving(false);
			mover.creature.cancelOffsetAnimation();
		}
		
		for (Mover mover : moves) {
			mover.moveBackToEmptyTile();
			mover.finish();
		}
		
		synchronized(this) {
			moves.clear();
		}
	}
	
	/**
	 * A class representing a single Movement of one creature
	 * @author Jared Stephen
	 *
	 */
	
	public class Mover {
		private Point initialPosition;
		private Creature creature;
		private List<Point> path;
		private long lastTime;
		private int lastIndex;
		
		private CombatRunner combatRunner;
		
		private boolean provokeAoOs;
		private List<Runnable> callbacks;
		
		private boolean finished;
		private long currentMoveIncrement;
		
		private int pauseCount;
		
		private boolean background;
		
		private boolean unpaused;
		
		/**
		 * Sets whether this is a background mover.  Background movers will not
		 * scroll the view.  By default, movers are not background movers
		 * @param background whether this is a background mover
		 */
		
		public void setBackground(boolean background) {
			this.background = background;
		}
		
		/**
		 * Adds all the specified Runnable objects to be run when this movement completes
		 * @param callbacks the list of callbacks to add
		 */
		
		public void addCallbacks(List<Runnable> callbacks) {
			this.callbacks.addAll(callbacks);
		}
		
		/**
		 * Sets whether the movement from this move will provoke attacks of
		 * opportunity from hostiles.  By default, moves do provoke these attacks
		 * @param provoke
		 */
		
		public void setProvokesAoOs(boolean provoke) {
			this.provokeAoOs = provoke;
		}
		
		/**
		 * Returns true if this Movement is completed, false otherwise
		 * @return true if this Movement has been completed
		 */
		
		public boolean isFinished() {
			return finished;
		}
		
		public Point getNextPosition() {
			return path.get(lastIndex - 1);
		}
		
		/**
		 * Returns the creature that is moving via this Mover
		 * @return the creature this is moving
		 */
		
		public Creature getCreature() {
			return creature;
		}
		
		/**
		 * Adds one to the pause count for this Mover.  The mover will become paused whenever
		 * the pause count is greater than 0.  This should be called by a pauser to temporarily
		 * pause movement for this specified Mover.
		 */
		
		public void incrementPauseCount() {
			pauseCount++;
		}
		
		/**
		 * Subtracts one from the pause count for this Mover.  The mover will remain paused
		 * as long as the pause count is greater than 0.  This should be called by a pauser
		 * when its reason for pausing the move is complete.
		 */
		
		public void decrementPauseCount() {
			pauseCount--;
			
			if (pauseCount == 0) {
				lastTime = System.currentTimeMillis();
				unpaused = true;
			}
		}
		
		private Mover(Creature creature, List<Point> path, boolean provokeAoOs) {
			this.provokeAoOs = provokeAoOs;
			this.callbacks = new ArrayList<Runnable>();
			this.creature = creature;
			
			this.path = new ArrayList<Point>();
			for (Point p : path) {
				this.path.add(new Point(p));
			}
			
			// save the creature's initial position for moving back to it
			// if needed
			this.initialPosition = creature.getPosition();
			
			this.lastTime = System.currentTimeMillis();
			this.lastIndex = path.size();
			
			this.combatRunner = Game.areaListener.getCombatRunner();
			this.finished = false;
			this.pauseCount = 0;
			this.currentMoveIncrement = Game.config.getCombatDelay();
			
			this.unpaused = false;
		}
		
		private void checkAoOsAndAnimate() {
			// if an AoO is provoked, pause this movement until the AoO unpauses it
			if (provokeAoOs && combatRunner.provokeAttacksOfOpportunity(creature, this)) {
				// if there were any AoOs, don't perform any movement this iteration
				this.currentMoveIncrement = Game.config.getCombatDelay() * 5;
				return;
			} else {
				// if not finished, add animation for next tile
				animateMovement(path.get(lastIndex - 1));
			}
		}
		
		private void animateMovement(Point dest) {
			Point curScreen = AreaUtil.convertGridToScreen(creature.getPosition());
			Point destScreen = AreaUtil.convertGridToScreen(dest);
			
			EntityMovementAnimation animation = new EntityMovementAnimation(creature,
					destScreen.x - curScreen.x, destScreen.y - curScreen.y, 0, 0);
			creature.addOffsetAnimation(animation);
			Game.particleManager.addEntityOffsetAnimation(animation);
		}
		
		private boolean isReadyForMovement(long curTime) {
			if (pauseCount == 0) {
				if (unpaused && curTime > lastTime + currentMoveIncrement - Game.config.getCombatDelay()) {
					// if unpaused, add the animation for the next movement tile
					unpaused = false;
					if (lastIndex > 0) {
						animateMovement(path.get(lastIndex - 1));
					}
				}
				
				return curTime > lastTime + currentMoveIncrement;
			}
			
			return false;
		}
		
		private void performMovement(long curTime) {
			lastTime = curTime;
			
			lastIndex--;
			
			// remove the AP cost from the mover
			creature.getTimer().move( path.get(lastIndex) );
			
			// set the new position
			boolean interrupted = creature.setPosition( path.get(lastIndex) );
			
			creature.setVisibility(false);
			
			if (creature.isPlayerSelectable()) {
				if (combatRunner.checkAIActivation()) {
					interrupted = true;
				}
			}
			
			// update the interface (especially the AP bar)
			Game.mainViewer.updateInterface();
			
			// set visibility for PCs
			if (creature.isPlayerSelectable()) {
				Game.areaListener.getAreaUtil().setPartyVisibility(Game.curCampaign.curArea);
			}
			
			// if interrupted, set global interrupt to stop all movement
			if (interrupted) {
				MovementHandler.this.interrupted = true;
			}
			
			this.currentMoveIncrement = Game.config.getCombatDelay();
			
			// if we are finished moving
			if (lastIndex == 0 || MovementHandler.this.interrupted) {
				moveBackToEmptyTile();
				
				if (!creature.isPlayerSelectable())
					this.currentMoveIncrement = Game.config.getCombatDelay() * 5;
				
				if (!background)
					Game.areaViewer.addDelayedScrollToCreature(creature);
			} else {
				checkAoOsAndAnimate();
			}
		}
		
		private void finish() {
			creature.setCurrentlyMoving(false);
			
			for (Runnable callback : callbacks) {
				callback.run();
			}
			
			finished = true;
			
			synchronized(this) {
				this.notifyAll();
			}
		}
		
		private void moveBackToEmptyTile() {
			// allow player to move all the way back to their initial position
			path.add(initialPosition);
			
			// save the initial screen position
			Point initialScreen = creature.getScreenPosition();
			int initialX = initialScreen.x + creature.getAnimatingOffsetPoint().x;
			int initialY = initialScreen.y + creature.getAnimatingOffsetPoint().y;
			
			for (int index = lastIndex; index < path.size(); index++) {
				Point p = path.get(index);
				
				Set<Entity> entities = Game.curCampaign.curArea.getEntities().getEntitiesSet(p.x, p.y);
				
				int creatureCount = 0;
				
				if (entities != null) {
					for (Entity entity : entities) {
						if (entity != creature && entity.getType() == Entity.Type.CREATURE) {
							Creature creature = (Creature)entity;
							
							// if the other creature on this tile is still moving,
							// allow this creature to stay
							if (!creature.isCurrentlyMoving()) 
								creatureCount++;
						}
					}
				}
				
				// we have found an empty tile
				if (creatureCount == 0) {
					// if this is the last index, the creature is already on the found tile
					if (index != lastIndex) {
						creature.setPosition(p);
						creature.setVisibility(false);
					}
					
					break;
				}
			}
			
			// figure out the animation to smoothly move the player to the new point
			Point newScreen = creature.getScreenPosition();
			
			int deltaX = initialX - newScreen.x;
			int deltaY = initialY - newScreen.y;
			
			EntityMovementAnimation animation = new EntityMovementAnimation(creature,
					-deltaX, -deltaY, deltaX, deltaY);
			creature.addOffsetAnimation(animation);
			Game.particleManager.addEntityOffsetAnimation(animation);
			
			// set the initial animating offset so that if the frame is drawn
			// before the animation starts, there is no "jump"
			creature.getAnimatingOffsetPoint().x = deltaX;
			creature.getAnimatingOffsetPoint().y = deltaY;
		}
	}
}
