/*
 * Hale is highly moddable tactical RPG.
 * Copyright (C) 2012 Jared Stephen
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

package net.sf.hale.swingeditor;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

import javax.swing.JFrame;

import net.sf.hale.Config;
import net.sf.hale.EntityManager;
import net.sf.hale.Game;
import net.sf.hale.loading.AsyncTextureLoader;
import net.sf.hale.resource.ResourceManager;
import net.sf.hale.rules.Dice;
import net.sf.hale.rules.Ruleset;
import net.sf.hale.util.JSEngineManager;

/**
 * A campaign editor using swing widgets rather than TWL
 * @author Jared
 *
 */

public class SwingEditor extends JFrame implements ComponentListener {
	/**
	 * The main entry point for the editor.  Any arguments are ignored
	 * @param args
	 */
	
	public static void main(String[] args) {
		// create the basic objects used by the campaign editor
		Game.textureLoader = new AsyncTextureLoader();
		Game.config = new Config();
		Game.scriptEngineManager = new JSEngineManager();
		Game.entityManager = new EntityManager();
		Game.dice = new Dice();
		
		ResourceManager.registerCorePackage();
		
		Game.ruleset = new Ruleset();
		
		// create the editor frame
		SwingEditor editor = new SwingEditor();
		
		EditorManager.initialize(editor);
		
		editor.setVisible(true);
	}

	private EditorMenuBar menuBar;

	private Canvas canvas;
	private OpenGLThread glThread;
	
	private SwingEditor() {
		addComponentListener(this);
		setSize(Game.config.getEditorResolutionX(), Game.config.getEditorResolutionY());
		setTitle("Hale Campaign Editor");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		menuBar = new EditorMenuBar(this);
		setJMenuBar(menuBar);
		
		// set up the OpenGL canvas
		canvas = new Canvas() {
			@Override public void addNotify() {
				super.addNotify();
				glThread = new OpenGLThread(canvas);
				glThread.start();
			}
			
			@Override public void removeNotify() {
				glThread.destroyDisplay();
			}
		};
		add(canvas, BorderLayout.CENTER);
	}
	
	/**
	 * Shows the specified log entry in the upper right of the editor
	 * @param entry the entry to add
	 */
	
	public void setLogEntry(String entry) {
		menuBar.setLogText(entry);
	}
	
	/**
	 * Returns the Canvas that the OpenGL context is drawing on
	 * @return the OpenGL canvas
	 */
	
	public Canvas getOpenGLCanvas() {
		return canvas;
	}
	
	/**
	 * Sets the AreaViewer that is used to draw the OpenGL content
	 * @param viewer
	 */
	
	public void setAreaViewer(AreaRenderer viewer) {
		if (glThread != null)
			glThread.setAreaViewer(viewer);
	}

	@Override public void componentHidden(ComponentEvent arg0) {
		if (glThread != null)
			glThread.setDrawingEnabled(false);
	}

	@Override public void componentMoved(ComponentEvent arg0) { }

	@Override public void componentResized(ComponentEvent arg0) {
		if (glThread != null)
			glThread.canvasResized();
	}

	@Override public void componentShown(ComponentEvent arg0) {
		if (glThread != null)
			glThread.setDrawingEnabled(true);
	}
}
