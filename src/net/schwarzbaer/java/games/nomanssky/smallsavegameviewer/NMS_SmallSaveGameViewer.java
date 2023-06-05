package net.schwarzbaer.java.games.nomanssky.smallsavegameviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.games.nomanssky.smallsavegameviewer.NMS_SmallSaveGameViewer.AppSettings.ValueKey;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.lib.system.Settings;

public class NMS_SmallSaveGameViewer {
	
	private static final String FILENAME_SLOT_DESCRIPTIONS = "NMS_SmallSaveGameViewer.SlotDescriptions.txt";
	private static final AppSettings settings = new AppSettings();
	private static final HashMap<Integer, String> slotDescriptions = new HashMap<Integer,String>();
	
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
		readSlotDescriptions();
		scanFolder();
	}

	private static String getLineValue(String line, String prefix) {
		if (line.startsWith(prefix))
			return line.substring(prefix.length());
		return null;
	}

	private static void readSlotDescriptions() {
		slotDescriptions.clear();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(FILENAME_SLOT_DESCRIPTIONS), StandardCharsets.UTF_8))) {
			
			String line, valueStr;
			while ( (line=in.readLine())!=null ) {
				
				if ( (valueStr=getLineValue(line, "Slot|"))!=null ) {
					int pos = valueStr.indexOf('|');
					String numberStr   = pos<0 ? "" : valueStr.substring(0, pos);
					String description = pos<0 ? "" : valueStr.substring(pos+1);
					try { slotDescriptions.put(Integer.parseInt(numberStr), description); }
					catch (NumberFormatException e) {}
				}
				
			}
			
		}
		catch (FileNotFoundException e) {
			//e.printStackTrace();
		} catch (IOException e) {
			System.err.printf("IOException while reading SlotDescriptions: %s%n", e.getMessage());
			// e.printStackTrace();
		}
	}

	private static void writeSlotDescriptions() {
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(FILENAME_SLOT_DESCRIPTIONS), StandardCharsets.UTF_8))) {
			
			Vector<Integer> slotNumbers = new Vector<>(slotDescriptions.keySet());
			slotNumbers.sort(null);
			for (Integer slot : slotNumbers) {
				if (slot==null) continue;
				String description = slotDescriptions.get(slot);
				if (!description.isEmpty())
					out.printf("Slot|%d|%s%n", slot, description);
			}
			
		} catch (FileNotFoundException e) {
			System.err.printf("FileNotFoundException while writing SlotDescriptions: %s%n", e.getMessage());
			// e.printStackTrace();
		}
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
				if (sg.slotNumber==saveGame.slotNumber) {
					sg.setGroupSibling(saveGame);
					saveGame.setGroupSibling(sg);
				}
			});
			
			saveGames.add(saveGame);
		}
		System.out.printf("%d SaveGames found%n", saveGames.size());
		
		saveGames.sort(Comparator.<SaveGame,Integer>comparing(sg->sg.fileNumber).thenComparing(sg->sg.file.getName()));
		
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
		private final int fileNumber;
		private final int slotNumber;
		private final Type type;
		
		private boolean isNewFormat = false;
		private int header = 0;
		private Integer version = null;
		private Integer timeAlive = null;
		private Integer totalPlayTime = null;
		private Integer units = null;
		private Integer nanites = null;
		private Integer quicksilver = null;
		private SaveGame slotSibling = null;
		
		SaveGame(File file, int fileNumber) {
			this.file = file;
			this.fileNumber = fileNumber;
			type = (this.fileNumber & 0x1)==0 ? Type.Manual : Type.Automatic;
			slotNumber = (this.fileNumber+1) >> 1;
		}

		void setGroupSibling(SaveGame groupSibling) {
			this.slotSibling = groupSibling;
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
			if (slotSibling==null) return true;
			
			boolean isOlder = file.lastModified()<slotSibling.file.lastModified();
			if (timeAlive    !=null && slotSibling.timeAlive    !=null && isOlder!=(timeAlive    <slotSibling.timeAlive    )) return false;
			if (totalPlayTime!=null && slotSibling.totalPlayTime!=null && isOlder!=(totalPlayTime<slotSibling.totalPlayTime)) return false;
			return true;
		}

		Boolean isOlderThanSibling(SaveGameTableModel.ColumnID columnID) {
			if (columnID==null) return null;
			if (slotSibling==null) return null;
			
			switch (columnID) {
			case FileDate     : return file.lastModified()<slotSibling.file.lastModified();
			case TimeAlive    : return timeAlive    ==null || slotSibling.timeAlive    ==null ? null : timeAlive<slotSibling.timeAlive;
			case TotalPlayTime: return totalPlayTime==null || slotSibling.totalPlayTime==null ? null : totalPlayTime<slotSibling.totalPlayTime;
			default           : return null;
			}
		}
		
	}

	private static class SaveGameTableModel extends Tables.SimplifiedTableModel<SaveGameTableModel.ColumnID> {
		
		private enum Alignment {
			Left  (SwingConstants.LEFT  ),
			Right (SwingConstants.RIGHT ),
			Center(SwingConstants.CENTER),
			;
			private final int constant;
			Alignment(int constant) { this.constant = constant; }
		}
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			FileNumber   ("#"            ,       Integer.class,  25, Alignment.Center),
			File         ("File"         ,        String.class,  70),
			FileDate     ("Date"         ,        String.class, 110),
			Slot         ("Slot"         ,       Integer.class,  30, Alignment.Center),
			Description  ("Description"  ,        String.class, 200, Alignment.Left),
			Type         ("Type"         , SaveGame.Type.class,  65, Alignment.Center),
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
			private final Alignment alignment;
			ColumnID(String name, Class<?> columnClass, int width) {
				this(name, columnClass, width, Alignment.Right);
			}
			ColumnID(String name, Class<?> columnClass, int width, Alignment alignment) {
				this.alignment = alignment;
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
			RightStringRenderer renderer = new RightStringRenderer();
			table.setDefaultRenderer(Integer.class, renderer);
			table.setDefaultRenderer(String.class, renderer);
			table.setDefaultRenderer(SaveGame.Type.class, renderer);
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
				
				int alignment = columnID==null ? SwingConstants.RIGHT : columnID.alignment.constant;
				String valueStr = value==null ? null : value.toString();
				rendererComponent.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
				rendererComponent.setHorizontalAlignment(alignment);
				
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
			case FileNumber   : return row.fileNumber;
			case File         : return row.file.getName();
			case FileDate     : return toDateStr(row.file.lastModified());
			case Slot         : return row.slotNumber;
			case Description  : return slotDescriptions.get(row.slotNumber);
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

		@Override
		protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID) {
			return columnID==ColumnID.Description;
		}

		@Override
		protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID) {
			SaveGame row = getRow(rowIndex);
			if (row==null) return;
			
			switch (columnID) {
			
			case Description:
				if (aValue instanceof String) slotDescriptions.put(row.slotNumber, (String) aValue);
				else if (aValue==null)        slotDescriptions.remove(row.slotNumber);
				SwingUtilities.invokeLater(()->{
					fireTableColumnUpdate(ColumnID.Description);
					writeSlotDescriptions();
				});
				break;
				
			default:
				break;
				
			}
		}

		private static String toDateStr(long time_ms) {
			return dateTimeFormatter.getTimeStr(time_ms, false, true, false, true, false);
		}

		private static String toTimeStr(Integer time_s) {
			if (time_s==null) return null;
			return DateTimeFormatter.getDurationStr(time_s.longValue());
		}
	
	}
}
