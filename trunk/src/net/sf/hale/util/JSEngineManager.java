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

package net.sf.hale.util;

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngineManager;

public class JSEngineManager {
	private final ScriptEngineManager manager;
	
	private final List<JSEngine> engines;
	
	public JSEngineManager() {
		this.manager = new ScriptEngineManager();
		this.engines = new ArrayList<JSEngine>();
	}
	
	public synchronized JSEngine getEngine() {
		for (JSEngine engine : engines) {
			if (!engine.inUse()) {
				engine.setInUse(true);
				return engine;
			}
		}
		
		JSEngine engine = new JSEngine(manager);
		engine.setInUse(true);
		engines.add(engine);
		return engine;
	}
	
	public JSEngine getPermanentEngine() {
		JSEngine engine = new JSEngine(manager);
		engine.setInUse(true);
		return engine;
	}
}
