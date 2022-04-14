package net.schwarzbaer.java.games.nomanssky.smallsavegameviewer;

import javax.swing.JFileChooser;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.system.Settings;

public class NMS_SmallSaveGameViewer {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new NMS_SmallSaveGameViewer().initialize();
	}
	
	private final JFileChooser folderChooser;

	private NMS_SmallSaveGameViewer() {
		folderChooser = new JFileChooser("./");
		new StandardMainWindow("No Man's Sky - Small SaveGame Viewer");
	}


	private void initialize() {
		// TODO Auto-generated method stub
		
	}


	static class AppSettings extends Settings.DefaultAppSettings<AppSettings.ValueGroup, AppSettings.ValueKey> {
		public enum ValueKey {
		}

		enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}
		
		public AppSettings() { super(NMS_SmallSaveGameViewer.class, ValueKey.values()); }
	}
}
