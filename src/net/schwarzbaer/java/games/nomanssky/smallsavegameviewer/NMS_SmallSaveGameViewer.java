package net.schwarzbaer.java.games.nomanssky.smallsavegameviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.java.games.nomanssky.smallsavegameviewer.NMS_SmallSaveGameViewer.AppSettings.ValueKey;
import net.schwarzbaer.system.DateTimeFormatter;
import net.schwarzbaer.system.Settings;

public class NMS_SmallSaveGameViewer {
	
	private static final AppSettings settings = new AppSettings();
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		new NMS_SmallSaveGameViewer().initialize();
	}
	
	private final JFileChooser folderChooser;
	private final StandardMainWindow mainWindow;
	private final SaveGameTableModel tableModel;
	private final JTable table;

	private NMS_SmallSaveGameViewer() {
		mainWindow = new StandardMainWindow("No Man's Sky - Small SaveGame Viewer");
		
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		tableModel = new SaveGameTableModel();
		table = new JTable(tableModel);
		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(800, 600));
		
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		tableModel.setRenderers();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.getTableHeader().setReorderingAllowed(false);
		table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		JPanel contentPane = new JPanel(new BorderLayout(3, 3));
		contentPane.add(tableScrollPane, BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane,createMenuBar());
		settings.registerAppWindow(mainWindow);
	}


	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		
		JMenu filesMenu = menuBar.add(new JMenu("Files"));
		filesMenu.add(createMenuItem("Define SaveGame Folder ...", e->{
			folderChooser.setDialogTitle("Select SaveGame Folder");
			if (folderChooser.showOpenDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
			File saveGamesFolder = folderChooser.getSelectedFile();
			if (saveGamesFolder!=null && saveGamesFolder.isDirectory()) {
				settings.putFile(ValueKey.SaveGamesFolder, saveGamesFolder);
				scanFolder(saveGamesFolder);
			}
		}));
		filesMenu.add(createMenuItem("ReScan SaveGame Folder", e->{
			scanFolder();
		}));
		
		JMenu debugMenu = menuBar.add(new JMenu("Debug"));
		debugMenu.add(createMenuItem("Show Table Column Widths", e->{
			System.out.printf("ColumnWidths: %s%n", Tables.SimplifiedTableModel.getColumnWidthsAsString(table));
		}));
		
		return menuBar;
	}


	static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}


	private void initialize() {
		scanFolder();
	}


	private void scanFolder() {
		File saveGamesFolder = settings.getFile(ValueKey.SaveGamesFolder, null);
		System.out.printf("SaveGamesFolder: %s%n", saveGamesFolder);
		if (saveGamesFolder!=null && saveGamesFolder.isDirectory())
			scanFolder(saveGamesFolder);
	}

	private void scanFolder(File saveGamesFolder) {
		if (saveGamesFolder==null) throw new IllegalArgumentException();
		if (!saveGamesFolder.isDirectory()) throw new IllegalArgumentException();
		
		System.out.printf("Scan Folder \"%s\" ...%n", saveGamesFolder);
		Vector<SaveGame> saveGames = new Vector<>();
		File[] files = saveGamesFolder.listFiles((FileFilter) File::isFile);
		System.out.printf("%d files found%n", files==null ? 0 : files.length);
		for (File file : files) {
			if (file==null    ) throw new IllegalStateException();
			if (!file.isFile()) throw new IllegalStateException();
			String filename = file.getName();
			
			Integer number = SaveGame.getNumber(filename);
			if (number == null) continue;
			
			SaveGame saveGame = new SaveGame(file,number);
			saveGame.scanFile();
			
			saveGames.forEach(sg->{
				if (sg.groupNumber==saveGame.groupNumber) {
					sg.setGroupSibling(saveGame);
					saveGame.setGroupSibling(sg);
				}
			});
			
			saveGames.add(saveGame);
		}
		System.out.printf("%d SaveGames found%n", saveGames.size());
		
		saveGames.sort(Comparator.<SaveGame,Integer>comparing(sg->sg.number).thenComparing(sg->sg.file.getName()));
		
		tableModel.setData(saveGames);
	}


	static class AppSettings extends Settings.DefaultAppSettings<AppSettings.ValueGroup, AppSettings.ValueKey> {
		public enum ValueKey {
			SaveGamesFolder
		}
	
		enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}
		
		public AppSettings() { super(NMS_SmallSaveGameViewer.class, ValueKey.values()); }
	}
	
	private static class SaveGame {
		
		enum Type { Automatic, Manual }
		
		private final File file;
		private final int number;
		private final int groupNumber;
		private final Type type;
		
		private boolean isNewFormat = false;
		private int header = 0;
		private Integer version = null;
		private Integer timeAlive = null;
		private Integer totalPlayTime = null;
		private Integer units = null;
		private Integer nanites = null;
		private Integer quicksilver = null;
		private SaveGame groupSibling = null;
		
		SaveGame(File file, int number) {
			this.file = file;
			this.number = number;
			type = (this.number & 0x1)==0 ? Type.Manual : Type.Automatic;
			groupNumber = (this.number+1) >> 1;
		}

		void setGroupSibling(SaveGame groupSibling) {
			this.groupSibling = groupSibling;
		}

		void scanFile() {
			byte[] bytes;
			try { bytes = Files.readAllBytes(file.toPath()); }
			catch (IOException ex) {
				System.err.printf("IOException while SaveGame.scanFile: ", ex.getMessage());
				//ex.printStackTrace();
				return;
			}
			
			header = get4Bytes(bytes,0);
			int startPos;
			if (header == 0xFEEDA1E5) {
				isNewFormat = true;
				startPos = 16;
			} else {
				isNewFormat = false;
				startPos = 0;
			}
			
			version       = findInteger(bytes,startPos,"F2P");
			timeAlive     = findInteger(bytes,startPos,"i8O");
			totalPlayTime = findInteger(bytes,startPos,"Lg8");
			units         = findInteger(bytes,startPos,"wGS");
			nanites       = findInteger(bytes,startPos,"7QL");
			quicksilver   = findInteger(bytes,startPos,"kN;");
			
		}

		private Integer findInteger(byte[] bytes, int startPos, String label) {
			int pos = findPos(bytes, startPos, label);
			if (pos<0) return null;
			
			if (pos>=bytes.length || (bytes[pos]&0xFF)!=(int)'"') return null; pos++;
			if (pos>=bytes.length || (bytes[pos]&0xFF)!=(int)':') return null; pos++;
			
			String numberStr = "";
			if (pos<bytes.length && (bytes[pos]&0xFF)==(int)'-') {
				numberStr += '-';
				pos++;
			}
			
			while (pos<bytes.length) {
				if ( (bytes[pos]&0xF0)!=0x30 )
					break;
				numberStr += (char)bytes[pos];
				pos++;
			}
			if (numberStr.isEmpty()) return null;
			
			try { return Integer.parseInt(numberStr); }
			catch (NumberFormatException e) { return null; }
		}

		private int findPos(byte[] bytes, int startPos, String label) {
			if (bytes==null    ) throw new IllegalArgumentException();
			if (startPos<0     ) throw new IllegalArgumentException();
			if (label==null    ) throw new IllegalArgumentException();
			if (label.isEmpty()) throw new IllegalArgumentException();
			byte[] searchBytes = label.getBytes();
			for (int i=startPos; i<bytes.length; i++) {
				if (bytes[i]!=searchBytes[0]) continue;
				boolean isEqual = true;
				for (int j=1; j<searchBytes.length && isEqual; j++)
					isEqual = bytes[i+j]==searchBytes[j];
				if (isEqual) return i+searchBytes.length;
			}
			return -1;
		}

		private static int get4Bytes(byte[] bytes, int startPos) {
			if (startPos<0) throw new IllegalArgumentException();
			
			int value = 0;
			int mask1 = 0xFF;
			for (int i=0; i<4 && startPos+i<bytes.length; i++) {
				if (i==0)
					value = bytes[startPos+i] & 0xFF;
				else {
					value = (value & mask1) | ( (bytes[startPos+i] & 0xFF) << (i*8) );
					mask1 = (mask1<<8) | 0xFF;
				}
			}
			return value;
		}

		static Integer getNumber(String filename) {
			// save5.hg
			String prefix = "save";
			String suffix = ".hg";
			if (!filename.startsWith(prefix)) return null;
			if (!filename.endsWith  (suffix)) return null;
			
			String nrStr = filename.substring(prefix.length(), filename.length()-suffix.length());
			if (nrStr.isEmpty()) return 1;
			try {
				int number = Integer.parseInt(nrStr);
				if (nrStr.equals(Integer.toString(number)))
					return number;
			} catch (NumberFormatException ex) {}
			
			return null;
		}

		boolean areDatesOkWithSibling() {
			if (groupSibling==null) return true;
			
			boolean isOlder = file.lastModified()<groupSibling.file.lastModified();
			if (timeAlive    !=null && groupSibling.timeAlive    !=null && isOlder!=(timeAlive    <groupSibling.timeAlive    )) return false;
			if (totalPlayTime!=null && groupSibling.totalPlayTime!=null && isOlder!=(totalPlayTime<groupSibling.totalPlayTime)) return false;
			return true;
		}

		Boolean isOlderThanSibling(SaveGameTableModel.ColumnID columnID) {
			if (columnID==null) return null;
			if (groupSibling==null) return null;
			
			switch (columnID) {
			case FileDate     : return file.lastModified()<groupSibling.file.lastModified();
			case TimeAlive    : return timeAlive    ==null || groupSibling.timeAlive    ==null ? null : timeAlive<groupSibling.timeAlive;
			case TotalPlayTime: return totalPlayTime==null || groupSibling.totalPlayTime==null ? null : totalPlayTime<groupSibling.totalPlayTime;
			default           : return null;
			}
		}
		
	}

	private static class SaveGameTableModel extends Tables.SimplifiedTableModel<SaveGameTableModel.ColumnID> {

		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Number       ("No"           ,       Integer.class,  25),
			Name         ("Name"         ,        String.class,  70),
			FileDate     ("Date"         ,        String.class, 110),
			Group        ("Group"        ,       Integer.class,  40),
			Type         ("Type"         , SaveGame.Type.class,  65),
			Header       ("Header"       ,        String.class,  80),
			NewFormat    ("New Format"   ,       Boolean.class,  75),
			Version      ("Version"      ,       Integer.class,  50),
			TimeAlive    ("TimeAlive"    ,        String.class,  75),
			TotalPlayTime("TotalPlayTime",        String.class,  85),
			Units        ("Units"        ,        String.class,  85),
			Nanites      ("Nanites"      ,        String.class,  55),
			Quicksilver  ("Quicksilver"  ,        String.class,  70),
			;
			private final Tables.SimplifiedColumnConfig config;
			ColumnID(String name, Class<?> columnClass, int width) {
				config = new Tables.SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
			}
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return config; }
		}

		private static final DateTimeFormatter dateTimeFormatter = new DateTimeFormatter();
		private Vector<SaveGame> data;

		SaveGameTableModel() {
			super(ColumnID.values());
			data = null;
		}
		
		void setRenderers() {
			table.setDefaultRenderer(String.class, new RightStringRenderer());
		}
		
		private class RightStringRenderer implements TableCellRenderer {
			
			private static final Color COLOR_NEWER = new Color(0xBBFFBB);
			private static final Color COLOR_OLDER = new Color(0xFFBBBB);
			private Tables.LabelRendererComponent rendererComponent;

			RightStringRenderer() {
				rendererComponent = new Tables.LabelRendererComponent();
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				
				Supplier<Color> getCustomBackground = null;
				if (columnID==ColumnID.FileDate || columnID==ColumnID.TimeAlive || columnID==ColumnID.TotalPlayTime) {
					int rowM = table.convertRowIndexToModel(rowV);
					SaveGame sg = getRow(rowM);
					if (sg!=null && !sg.areDatesOkWithSibling()) {
						Boolean isOlder = sg.isOlderThanSibling(columnID);
						if (isOlder!=null)
							getCustomBackground = ()->isOlder ? COLOR_OLDER : COLOR_NEWER;
					}
				}
				
				rendererComponent.configureAsTableCellRendererComponent(table, null, value==null ? null : value.toString(), isSelected, hasFocus, getCustomBackground, null);
				rendererComponent.setHorizontalAlignment(SwingConstants.RIGHT);
				
				
				return rendererComponent;
			}
		}
		
		void setData(Vector<SaveGame> data) {
			this.data = data;
			fireTableUpdate();
		}

		@Override
		public int getRowCount() {
			return data==null ? 0 : data.size();
		}

		SaveGame getRow(int rowIndex) {
			if (data == null) return null;
			if (rowIndex < 0) return null;
			if (data.size() <= rowIndex) return null;
			return data.get(rowIndex);
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			SaveGame row = getRow(rowIndex);
			if (row==null) return null;
			
			switch (columnID) {
			case Number       : return row.number;
			case Name         : return row.file.getName();
			case FileDate     : return toDateStr(row.file.lastModified());
			case Group        : return row.groupNumber;
			case Type         : return row.type;
			case Header       : return String.format("0x%08X", row.header);
			case NewFormat    : return row.isNewFormat;
			case Version      : return row.version;
			case TimeAlive    : return toTimeStr(row.timeAlive);
			case TotalPlayTime: return toTimeStr(row.totalPlayTime);
			case Units        : return row.units      ==null ? null : String.format(Locale.ENGLISH, "%,d", row.units       & 0xFFFFFFFFL);
			case Nanites      : return row.nanites    ==null ? null : String.format(Locale.ENGLISH, "%,d", row.nanites     & 0xFFFFFFFFL);
			case Quicksilver  : return row.quicksilver==null ? null : String.format(Locale.ENGLISH, "%,d", row.quicksilver & 0xFFFFFFFFL);
			}
			
			return null;
		}

		private String toDateStr(long time_ms) {
			return dateTimeFormatter.getTimeStr(time_ms, false, true, false, true, false);
		}

		private String toTimeStr(Integer time_s) {
			if (time_s==null) return null;
			return DateTimeFormatter.getDurationStr(time_s.longValue());
		}
	
	}
}
