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

package net.sf.hale.defaultability;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import de.matthiasmann.twl.Button;

import net.sf.hale.Game;
import net.sf.hale.entity.Creature;
import net.sf.hale.util.LineParser;
import net.sf.hale.util.Logger;
import net.sf.hale.util.Point;
import net.sf.hale.widgets.RightClickMenu;

/**
 * A list that maps various mouse hover states to cursor images
 * and default left click actions.  Depending on what the mouse is hovering
 * over (a Creature, a Container, or a Door, for example), the currently
 * selected Entity, and other conditions, the mouse cursor and default left
 * click action will change.
 * 
 * @author Jared Stephen
 *
 */

public class MouseActionList {
	/**
	 * The list of all possible types of mouse cursor states.  Most
	 * Conditions have a corresponding DefaultAbility that they
	 * activate.  Note that the ordering of the Conditions is important
	 * as it determines the precedence of each Condition with ability
	 * when determining the default left click action.
	 * @author Jared Stephen
	 *
	 */
	
	public enum Condition {
		/** no available left click action */
		Cancel(null),
		
		/** a valid selection in targeting mode */
		TargetSelect(null),
		
		/** a valid target to add in targeting mode, but not to complete the selection */
		TargetSelectAdd(null),
		
		/** select the Entity being hovered */
		Select(new net.sf.hale.defaultability.Select()),
		
		/** attack the hovered Entity */
		Attack(new net.sf.hale.defaultability.Attack()),
		
		/** attempt to recover the Trap being hovered */
		RecoverTrap(new net.sf.hale.defaultability.RecoverTrap()),
		
		/** attempt to disarm the Trap being hovered */
		DisarmTrap(new net.sf.hale.defaultability.DisarmTrap()),
		
		/** attempt to pick the hovered locked Openable */
		PickLock(new net.sf.hale.defaultability.PickLock()),
		
		/** open the hovered Container */
		Container(new net.sf.hale.defaultability.OpenContainer()),
		
		/** open the hovered Door */
		Door(new net.sf.hale.defaultability.OpenDoor()),
		
		/** talk to the hovered Entity */
		Talk(new net.sf.hale.defaultability.Talk()),
		
		/** activate the hovered AreaTransition */
		Travel(new net.sf.hale.defaultability.Travel()),
		
		/** move the selected Entity to the hovered position */
		Move(new net.sf.hale.defaultability.Move()),
		
		/**
		 * show details for a specified creature.  This action is never a default left click action, it can
		 * only be accessed via a right click
		 */
		ExamineCreature(new net.sf.hale.defaultability.ExamineCreature()),
		
		/**
		 * show details for a specified item.  This action is never a default left click action, it can only
		 * be accessed via a right click
		 */
		ExamineItem(new net.sf.hale.defaultability.ExamineItem());
		
		private final DefaultAbility ability;
		
		private Condition(DefaultAbility ability) {
			this.ability = ability;
		}
		
		/**
		 * Returns an instance of this Condition's associated DefaultAbility
		 * @return this Condition's associated DefaultAbility
		 */
		
		public DefaultAbility getAbility() { 
			if (ability == null) return null;
			else return ability.getInstance();
		}
	};
	
	private final Map<Condition, String> mouseCursors;
	
	// the list of all conditions with a non-null ability.
	// this is used when constructing the default abilities menu
	// and when finding the default mouse condition.  Using this,
	// there is no needlessly checking Conditions that have no
	// associated ability
	private final ArrayList<Condition> conditionsWithAbility;
	
	/**
	 * Create a new MouseActionList from the data in the file at the specified
	 * location.  The list of conditions and associated cursors and actions
	 * will be read in from this file.
	 * 
	 * @param fileName the location of the mouseActions.txt file
	 */
	
	public MouseActionList(String fileName) {
		mouseCursors = new HashMap<Condition, String>();
		
		int lineNumber = 0;
		
		try {
			Scanner sFile = new Scanner(new File(fileName));
			LineParser sLine;
			String line;
			
			while (sFile.hasNextLine()) {
				line = sFile.nextLine();
		    	sLine = new LineParser(line);
		    	lineNumber++;
		    	
		    	if (sLine.hasNext()) {
		    		Condition condition = Condition.valueOf(sLine.next());
		    		String cursor = sLine.next();
		    		
		    		mouseCursors.put(condition, cursor);
		    	}
			}
			
			sFile.close();
			
		} catch (Exception e) {
			Logger.appendToErrorLog("Error parsing mouseAction file " + fileName + " on line " + lineNumber, e);
		}
		
		conditionsWithAbility = new ArrayList<Condition>();
		for (Condition condition : Condition.values()) {
			if (condition.ability != null) conditionsWithAbility.add(condition);
		}
		conditionsWithAbility.trimToSize();
	}
	
	/**
	 * Returns the cursor icon corresponding to the specified condition
	 * @param condition the condition to search for
	 * @return the cursor icon corresponding to the specified condition
	 */
	
	public String getMouseCursor(Condition condition) {
		return mouseCursors.get(condition);
	}
	
	/**
	 * Shows the RightClickMenu containing the available DefaultAbilities for given
	 * parent and targetPosition.
	 * 
	 * @param parent the currently selected Creature that will be performing one of
	 * the DefaultAbilities referenced in the menu.
	 * @param targetPosition the grid point position that the mouse has been clicked on
	 * @param x the screen point x position that the menu should be opened at
	 * @param y the screen point y position that the menu should be opened at
	 */
	
	public void showDefaultAbilitiesMenu(Creature parent, Point targetPosition, int x, int y) {
		RightClickMenu menu = Game.mainViewer.getMenu();
		
		menu.clear();
		
		menu.setPosition(x, y);
		menu.addMenuLevel("Actions");
		
		// keep track of how many buttons we have added so we can add a placeholder
		// if none are added
		int buttonsAdded = 0;
		
		for (Condition condition : conditionsWithAbility) {
			DefaultAbility ability = condition.getAbility();
			if (!ability.canActivate(parent, targetPosition)) continue;
			
			DefaultAbilityCallback callback = new DefaultAbilityCallback(ability);
			callback.setActivateParameters(parent, targetPosition);
			
			Button button = new Button(ability.getActionName());
			button.addCallback(callback);
			menu.addButton(button);
			
			buttonsAdded++;
		}
		
		if (buttonsAdded == 0) {
			Button button = new Button("None");
			button.setEnabled(false);
			menu.addButton(button);
		}
		
		menu.show();
	}
	
	/**
	 * Returns the Condition that is the default condition for the specified parent
	 * at the specified grid point.  This is the Condition that is first in the
	 * list of Conditions specified in the Condition enum definition, with an
	 * associated DefaultAbility that returns true to canActivate().
	 * 
	 * @param parent the parent Creature that will be performing the DefaultAbility
	 * associated with the Condition
	 * @param targetPosition the grid coordinates position
	 * @return the default Condition for the specified parameters in the current Area
	 */
	
	public Condition getDefaultMouseCondition(Creature parent, Point targetPosition) {
		for (Condition condition : conditionsWithAbility) {
			switch (condition) {
			// ExamineCreature and ExamineItem are never default left click actions
			case ExamineCreature: case ExamineItem:
				continue;
			}
			
			// no need to generate a new instance of the DefaultAbility with
			// getAbility() here, as we don't need to save the instance data
			// from canActivate
			if (condition.ability.canActivate(parent, targetPosition)) return condition;
		}
		
		return Condition.Cancel;
	}
}
