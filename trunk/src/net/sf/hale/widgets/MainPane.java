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

package net.sf.hale.widgets;

import net.sf.hale.Game;
import net.sf.hale.entity.Creature;
import net.sf.hale.interfacelock.MovementHandler;
import net.sf.hale.mainmenu.InGameMenu;
import net.sf.hale.view.GameSubWindow;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;

/**
 * The interface widget containing many of the buttons used to access
 * the various screens of the interface
 * @author Jared Stephen
 *
 */

public class MainPane extends Widget {
	public static final StateKey STATE_NOTIFICATION = StateKey.get("notification");
	
	private final Button endTurn;
	
	private final Button[] windowButtons;
	
	private final ToggleButton partyMovement, singleMovement;
	
	private int buttonGap;
	private int rowGap;
	
	/**
	 * Create a new Widget
	 */
	
	public MainPane() {
		this.setTheme("mainpane");
		
		windowButtons = new Button[5];
		
		windowButtons[0] = new Button();
		windowButtons[0].setTheme("menubutton");
		windowButtons[0].addCallback(new OpenMenuCallback());
		
		windowButtons[1] = new Button();
		windowButtons[1].setTheme("characterbutton");
		windowButtons[1].addCallback(new ToggleWindowCallback(Game.mainViewer.characterWindow));
		
		windowButtons[2] = new Button();
		windowButtons[2].setTheme("inventorybutton");
		windowButtons[2].addCallback(new ToggleWindowCallback(Game.mainViewer.inventoryWindow));
		
		windowButtons[3] = new Button();
		windowButtons[3].setTheme("mapbutton");
		windowButtons[3].addCallback(new ToggleWindowCallback(Game.mainViewer.miniMapWindow));
		
		windowButtons[4] = new LogButton();
		windowButtons[4].addCallback(new ToggleWindowCallback(Game.mainViewer.logWindow));
		
		for (Button button : windowButtons) {
			add(button);
		}
		
		endTurn = new Button();
		endTurn.setTheme("endturnbutton");
		endTurn.addCallback(new EndTurnCallback());
		add(endTurn);
		
		partyMovement = new ToggleButton();
		if (Game.interfaceLocker.getMovementMode() == MovementHandler.Mode.Party)
			partyMovement.setActive(true);
			
		partyMovement.addCallback(new Runnable() {
			@Override public void run() {
				partyMovement.setActive(true);
				singleMovement.setActive(false);
				
				setMovementMode();
			}
		});
		partyMovement.setTheme("partymovementbutton");
		add(partyMovement);
		
		singleMovement = new ToggleButton();
		if (Game.interfaceLocker.getMovementMode() == MovementHandler.Mode.Single)
			singleMovement.setActive(true);
		
		singleMovement.addCallback(new Runnable() {
			@Override public void run() {
				partyMovement.setActive(false);
				singleMovement.setActive(true);
				
				setMovementMode();
			}
		});
		singleMovement.setTheme("singlemovementbutton");
		add(singleMovement);
	}
	
	private void setMovementMode() {
		if (partyMovement.isActive())
			Game.interfaceLocker.setMovementMode(MovementHandler.Mode.Party);
		else
			Game.interfaceLocker.setMovementMode(MovementHandler.Mode.Single);
	}
	
	@Override protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		
		buttonGap = themeInfo.getParameter("buttonGap", 0);
		rowGap = themeInfo.getParameter("rowGap", 0);
	}
	
	/**
	 * Returns the minimum x position of this main pane that is occupied by buttons
	 */
	
	public int getButtonsMinX() {
		int minX = getInnerRight();
		for (Button button : windowButtons) {
			minX -= button.getPreferredWidth();
		}
		
		return minX - getBorderLeft();
	}
	
	@Override protected void layout() {
		super.layout();
		
		int lastX = getInnerRight();
		for (Button button : windowButtons) {
			button.setSize(button.getPreferredWidth(), button.getPreferredHeight());
			button.setPosition(lastX - button.getWidth(), getInnerY());
			lastX = button.getX() - buttonGap;
		}
		
		partyMovement.setSize(partyMovement.getPreferredWidth(), partyMovement.getPreferredHeight());
		singleMovement.setSize(singleMovement.getPreferredWidth(), singleMovement.getPreferredHeight());
		
		partyMovement.setPosition(windowButtons[4].getX(), windowButtons[4].getBottom() + rowGap);
		singleMovement.setPosition(partyMovement.getRight() + buttonGap, partyMovement.getY());
		
		endTurn.setSize(endTurn.getPreferredWidth(), endTurn.getPreferredHeight());
		
		endTurn.setPosition(getInnerRight() - endTurn.getWidth(),
				getInnerBottom() - endTurn.getHeight());
	}
	
	/**
	 * Sets the new log entry notification state to the specified value
	 * @param newLogEntry whether a  new log entry notification should be
	 * shown
	 */
	
	public void setLogNotification(boolean newLogEntry) {
		((LogButton)windowButtons[4]).setNotification(newLogEntry);
	}
	
	/**
	 * Resets the view of this Widget to the default state
	 */
	
	public void resetView() {
		((LogButton)windowButtons[4]).setNotification(false);
	}
	
	/**
	 * Updates the state of this widget with any changes
	 */
	
	public void update() {
		endTurn.setEnabled(isEndTurnEnabled());
		
		Creature current = Game.curCampaign.party.getSelected();
		if (Game.isInTurnMode()) current = Game.areaListener.getCombatRunner().lastActiveCreature();
		
		endTurn.getAnimationState().setAnimationState(STATE_NOTIFICATION, !current.getTimer().canAttack());
	}
	
	/**
	 * Returns true if the end turn functionality of the end turn button
	 * is currently enabled, false otherwise.  Note that this function can
	 * return a different value than the actual state of the end turn button.
	 * The end turn button is only updated when {@link #update()} is called
	 * @return whether the end turn functionality is enabled
	 */
	
	public boolean isEndTurnEnabled() {
		return !Game.interfaceLocker.locked() && Game.isInTurnMode() &&
			!Game.areaListener.getTargeterManager().isInTargetMode();
	}
	
	private class LogButton extends Button {
		private String notificationTooltip;
		
		@Override protected void applyTheme(ThemeInfo themeInfo) {
			super.applyTheme(themeInfo);
			
			notificationTooltip = themeInfo.getParameter("notificationTooltip", (String)null);
		}
		
		private void setNotification(boolean notification) {
			getAnimationState().setAnimationState(STATE_NOTIFICATION, notification);
			
			if (notification) {
				setTooltipContent(notificationTooltip);
			} else {
				setTooltipContent(getThemeTooltipContent());
			}
		}
	}
	
	private class ToggleWindowCallback implements Runnable {
		private final GameSubWindow window;
		
		private ToggleWindowCallback(GameSubWindow window) {
			this.window = window;
		}
		
		@Override public void run() {
			window.setVisible(!window.isVisible());
		}
	}
	
	private class EndTurnCallback implements Runnable {
		@Override public void run() {
			Game.areaListener.nextTurn();
		}
	}
	
	private class OpenMenuCallback implements Runnable {
		@Override public void run() {
			InGameMenu menu = new InGameMenu(Game.mainViewer);
			menu.openPopupCentered();
		}
	}
}
