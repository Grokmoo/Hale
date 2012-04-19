
/*
 * A simple AI script that attempts to activate damage, debuff, and buff type abilities
 * Once the creature runs out of abilities to activate, it falls back to the basic AI
 */

function runTurn(game, parent) {
	var aiSet = parent.getAbilities().createAISet();
	
	var allSlots = aiSet.getWithActionTypes(["Buff", "Debuff", "Damage", "Summon"]);
	
	// check if we have any valid abilities to use
	// if not, fall back to basic AI
	var numAbilities = allSlots.size();
	if (numAbilities == 0) {
		fallbackToBasicAI(game, parent);
		return;
	}
	
	// check to see if we have any healing spells
	var healingSlots = aiSet.getWithActionType("Heal");
	if (healingSlots.size() > 0) {
		// if we have a healing spell, check if there are any good targets
		
		var friendlies = game.ai.getLiveVisibleCreatures(parent, "Friendly");
		
		// check for a friendly below 1/2 Max HP
		for (var i = 0; i < friendlies.size(); i++) {
			var friendly = friendlies.get(i);
			
			if (friendly.getCurrentHP() / friendly.stats().getMaxHP() < 0.5) {
				// we found a good target; add the healing spells to the front of the slots list
				allSlots.addAll(0, healingSlots);
				break;
			}
		}
	}
	
	// go through all of the abilities in the list in order and try them until
	// we run out of AP
	for (var i = 0; i < allSlots.size(); i++) {
		var slot = allSlots.get(i);
		
		// first attempt to move within range as needed
		var targetData = moveTowardsForAbility(game, parent, slot);
		
		if (targetData.endTurn)
			return;
		
		if (!targetData.targetFound)
			continue;
		
		// now try to activate the ability
		var activateData = tryActivateAbility(game, parent, targetData.target, slot, aiSet);
			
		// we have activated one ability
		numAbilities--;
			
		// we can either end the turn here or try another ability
		if (activateData.endTurn)
			return;
	}

	// if all abilities have been used at this point, we may still have AP left
	// to fall back to basic AI
	if (numAbilities == 0) {
		fallbackToBasicAI(game, parent);
		return;
	}
}

function fallbackToBasicAI(game, parent) {
    game.runExternalScript("ai/aiBasic", "runTurn", parent);
}

function tryActivateAbility(game, parent, target, slot, aiSet) {
	var abilityID = slot.getAbility().getID();

	var targeter = aiSet.activateAndGetTargeter(slot);
	
	// if we could not activate the ability, it means we most likely don't
	// have enough AP and probably can't do anything useful
	if (targeter == null) {
		return { 'endTurn' : true };
	}
	
	// try to activate on our preferred target
	targeter.setMousePosition(target);
	
	// check to see if we have a valid selection here
	var condition = targeter.getMouseActionCondition().toString();
	
	if (condition.equals("TargetSelect")) {
		// activate the ability;
		targeter.performLeftClickAction();
		game.sleepStandardDelay(4);
		
		// we maybe have enough AP to activate another ability
		return { 'endTurn' : false };
	}
	
	// if we didn't have a valid selection with our target, check for
	// a list of valid selections
	var selectable = targeter.getAllowedPoints();
	
	if (!selectable.isEmpty()) {
		// targeter has specific clickable points, so choose the first one
		targeter.setMousePosition(selectable.get(0));
        targeter.performLeftClickAction();
		game.sleepStandardDelay(4);
		
		return { 'endTurn' : false };
	}
	
	// we weren't able to activate the targeter
	targeter.cancel();
	
	return { 'endTurn' : false };
}

function moveTowardsForAbility(game, parent, slot) {
	// if the slot cannot activate, it means we don't have enough AP
	// and probably can't do anything useful
	if (!slot.canActivate()) {
		return { 'endTurn' : true };
	}

	var ability = slot.getAbility();
	
	// figure out how far away we should be
	var rangeType = ability.getRangeType().toString();
	
	var preferredDistance = 0;
	if (rangeType.equals("Touch")) preferredDistance = 1;
	else if (rangeType.equals("Short")) preferredDistance = 2;
	else if (rangeType.equals("Long")) preferredDistance = 6;

	// find the best target for our ability
	var preferredTarget = findBestTarget(game, preferredDistance, parent, ability);
	
	// if no target was found, return and try a different ability
	if (preferredTarget == null) {
		return { 'endTurn' : false, 'targetFound' : false };
	}

	// now move towards the target as needed until we either run out of AP or are in position to
	// use the ability
	var curDistance = game.distance(parent, preferredTarget) / 5;
	
	while (curDistance > preferredDistance) {
		var moved = game.ai.moveTowards(parent, preferredTarget.getPosition(), preferredDistance);
		
		// if no movement occurred, we are probably out of AP or immobilized
		if (!moved) {
			return { 'endTurn' : true };
		}
		
		var curDistance = game.distance(parent, preferredTarget) / 5;
	}
	
	// make sure we still have enough AP to use the ability
	if (!slot.canActivate()) {
		return { 'endTurn' : true };
	}
	
	// at this point we should be able to activate the ability on our target
	return { 'endTurn' : false, 'targetFound' : true, 'target' : preferredTarget.getPosition() };
}

function findBestTarget(game, preferredDistance, parent, ability) {
	// figure out whether we should target friendlies or hostiles
	var actionType = ability.getActionType().toString();
	
	var targetRelationship = "Friendly";
	if (actionType.equals("Damage") || actionType.equals("Debuff")) {
		targetRelationship = "Hostile";
	}
	
	// if it is self targeted
	if (preferredDistance == 0) {
		return parent.getPosition();
	}
	
	// for summon spells, try to find an empty tile to summon the creature onto
	if (actionType.equals("Summon")) {
		var position = game.ai.findClosestEmptyTile(parent.getPosition(), preferredDistance);
		
		// we return the position that was found, even if it is null
		// this should be a very rare event since it requires all tiles around the caster
		// to be unusable
		return position;
	}
	
	// get the list of all targets sorted closest first
	var allTargets = game.ai.getLiveVisibleCreatures(parent, targetRelationship);
	game.ai.sortCreatureListClosestFirst(parent, allTargets);
	
	// find the best target based on action type
	if (actionType.equals("Heal")) {
		// find the most damaged friendly
		return findBestHealTarget(game, allTargets, parent, ability);
	} else {
		// find the closest target match
		return findClosestTarget(game, allTargets, parent, ability);
	}
}

/*
 * it is assumed that the creatures array is already sorted closest first
 */

function findBestHealTarget(game, creatures, parent, ability) {
	var bestTarget = null;
	var bestHP = 1.0;
	
	for (var i = 0; i < creatures.size(); i++) {
		var hpFraction = creatures.get(i).getCurrentHP() / creatures.get(i).stats().getMaxHP();
		
		// favor earlier (closer) entries in the inequality when ties arise
		if (hpFraction < bestHP) {
			bestTarget = creatures.get(i);
			bestHP = hpFraction;
		}
	}
	
	return bestTarget;
}

/*
 * it is assumed that the creatures array is already sorted closest first
 */

function findClosestTarget(game, creatures, parent, ability) {
	var preferredTarget = null;
	
	if (ability.hasFunction("isTargetValid")) {
		// the ability can validate targets
		for (var i = 0; i < creatures.size(); i++) {
			var curTarget = creatures.get(i);
		
			var isValid = ability.executeFunction("isTargetValid", curTarget);
			
			if (isValid == true) {
				preferredTarget = curTarget;
				break;
			}
		}
	} else {
		// the ability does not validate targets so just pick the closest one
		if (creatures.size() > 0)
			preferredTarget = creatures.get(0);
	}
	
	return preferredTarget;
}

