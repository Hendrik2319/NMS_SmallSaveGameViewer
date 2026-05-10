package net.schwarzbaer.java.games.nomanssky.smallsavegameviewer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

class SaveGameDecoder
{
	static final int VERBOSE_LEVEL_Nothing = 0;
	static final int VERBOSE_LEVEL_Files_Only = 1;
	static final int VERBOSE_LEVEL_Blocks_Only = 2;
	static final int VERBOSE_LEVEL_Blocks_and_Sequences = 3;

	static byte[] decodeFile(File file)
	{
		return decodeFile(file, VERBOSE_LEVEL_Nothing);
	}

	static byte[] decodeFile(File file, int verboseLevel)
	{
		if (!file.isFile()) return null;
		
		if (verboseLevel>=VERBOSE_LEVEL_Blocks_Only)
			Gui.log_ln("Read file \"%s\" ...", file.getAbsolutePath());
		else if (verboseLevel==VERBOSE_LEVEL_Files_Only)
			Gui.log("Read file \"%s\" ...", file.getAbsolutePath());
		
		byte[] bytes;
		try { bytes = Files.readAllBytes(file.toPath()); }
		catch (IOException e)
		{
			Gui.log_error_ln("%s occured while reading file \"%s\" : %s", e.getClass().getCanonicalName(), file.getAbsolutePath(), e.getMessage());
			// e.printStackTrace();
			return null;
		}
		
		return decodeFileContent(bytes, file.getAbsolutePath(), verboseLevel);
	}

	static byte[] decodeFileContent(byte[] bytes, String filepath)
	{
		return decodeFileContent(bytes, filepath, VERBOSE_LEVEL_Nothing);
	}

	static byte[] decodeFileContent(byte[] bytes, String filepath, int verboseLevel)
	{
		if (bytes.length == 0)
		{
			if (verboseLevel>=VERBOSE_LEVEL_Blocks_Only)
				Gui.log_ln("   is empty");
			else if (verboseLevel==VERBOSE_LEVEL_Files_Only)
				Gui.log(" is empty");
		}
		else if ((bytes[0] & 0xFF) == '{')
		{
			if (verboseLevel>=VERBOSE_LEVEL_Blocks_Only)
				Gui.log_ln("   is plain text");
			else if (verboseLevel==VERBOSE_LEVEL_Files_Only)
				Gui.log(" is plain text");
		}
		else
		{
			if (verboseLevel==VERBOSE_LEVEL_Files_Only)
				Gui.log(" is encoded");
			bytes = decodeFileContent_intern(bytes, filepath, verboseLevel);
		}
		
		if (verboseLevel>=VERBOSE_LEVEL_Blocks_Only)
			Gui.log_ln( "... done (%d bytes)", bytes.length);
		else if (verboseLevel==VERBOSE_LEVEL_Files_Only)
			Gui.log_ln(" ... done (%d bytes)", bytes.length);
		
		return bytes;
	}

	private static byte[] decodeFileContent_intern(byte[] bytes, String filepath, int verboseLevel)
	{
		RawBytesReader in = new RawBytesReader(bytes);
		Output out = new Output(new OutputContext() {
			@Override public int getReadPos() { return in.getPos(); }
		});
		boolean showLiterals = false;
		
		try
		{
			int blockIndex = 0;
			while (!in.isEOF())
			{
				out.resetBuffer();
				if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
					Gui.log_ln("Blocks[%d]", ++blockIndex);
				
				int magicNumber = in.readFourByte();
				if (magicNumber!=0xFEEDA1E5)
					throw new FormatException("wrong magic number (0x%X found, 0xFEEDA1E5 expected)", magicNumber);
				
				int compressedSize   = in.readFourByte();
				int uncompressedSize = in.readFourByte();
				int reserved         = in.readFourByte();
				if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
				{
					Gui.log_ln("    magic number     : %X (%d)", magicNumber, magicNumber);
					Gui.log_ln("    uncompressed size: %X (%d)", uncompressedSize, uncompressedSize);
					Gui.log_ln("    compressed size  : %X (%d)", compressedSize, compressedSize);
					Gui.log_ln("    reserved size    : %X (%d)", reserved, reserved);
				}
				else if (verboseLevel==VERBOSE_LEVEL_Blocks_Only)
					Gui.log("    Blocks[%03d] { magic:OK, encoded size: %d, decoded size: %d }", ++blockIndex, compressedSize, uncompressedSize);
				
				int pos = in.getPos();
				int blockEnd = pos + compressedSize;
				
				while (pos < blockEnd)
				{
					readSequence(in, out, blockEnd, verboseLevel, showLiterals);
					pos = in.getPos();
				}
				
				if (pos > blockEnd)
					throw new FormatException("Unexpected stream position after last sequence of block: current pos: %d; expected pos: %d", pos, blockEnd);
				
				if (verboseLevel==VERBOSE_LEVEL_Blocks_Only)
					Gui.log_ln("  [out.pos:%016X]", out.out.size());
			}
		}
		catch (ReadException | FormatException ex)
		{
			Gui.log_error_ln("%s occured while decoding file \"%s\" : %s", ex.getClass().getCanonicalName(), filepath, ex.getMessage());
			// ex.printStackTrace();
			return null;
		}
		
		return out.getAllBytes();
	}
	
	private static void readSequence(RawBytesReader in, Output out, int blockEnd, int verboseLevel, boolean showLiterals) throws ReadException, FormatException
	{
		int pos = in.getPos();
		int totalSize = in.getTotalSize();
		int token = in.readOneByte();
		int literalsCount0 = (token & 0xF0) >> 4;
		int matchLength0   = (token & 0x0F) >> 0;
		if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
		{
			Gui.log("    [%016X,%5.1f%%] Sequence {", pos, pos*100.0/totalSize);
			Gui.log(" token:0x%02X (lit0:%2d,mat0:%2d)", token, literalsCount0, matchLength0);
		}
		
		int literalsCount = readVarLength(in, literalsCount0);
		if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
			Gui.log(", lit:0x%02X(%3d)", literalsCount, literalsCount);
		
		if (showLiterals) Gui.log(", lit[]:");
		out.copyLiterals(in,literalsCount,showLiterals);
		
		pos = in.getPos();
		if (pos < blockEnd)
		{
			int offset = in.readTwoByte();
			if (offset==0)
				throw new FormatException("Wrong offset: Offset==0 (InputReadPos:%d)", in.getPos());
			
			int matchLength = readVarLength(in, matchLength0) + 4;
			if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
			{
				Gui.log(", offset:0x%04X(%2d)", offset, offset);
				Gui.log(", mat:0x%02X(%3d)", matchLength, matchLength);
			}

			if (showLiterals) Gui.log(", mat[]:");
			out.copyMatch(offset,matchLength,showLiterals);
		}
		else
		{
			if (pos > blockEnd)
				throw new FormatException("Unexpected stream position after last sequence of block: current pos: %d; expected pos: %d", pos, blockEnd);
			if (matchLength0!=0)
				throw new FormatException("Unexpected matchLength in last sequence of block: found: %d; expected: 0", matchLength0);
		}
		
		if (verboseLevel>=VERBOSE_LEVEL_Blocks_and_Sequences)
			Gui.log_ln(" } [out.pos:%016X]", out.out.size());
	}

	private static int readVarLength(RawBytesReader in, int val0) throws ReadException
	{
		int val = val0;
		if (val0==15)
		{
			int extra = in.readOneByte();
			while (extra==255)
			{
				val += 255;
				extra = in.readOneByte();
			}
			val += extra;
		}
		return val;
	}
	
	private interface OutputContext
	{
		int getReadPos();
	}
	
	private static class Output
	{
		private final OutputContext context;
		private final ByteArrayOutputStream out;
		private final byte[] buffer;
		private int writePos;
		private boolean firstRun;

		Output(OutputContext context)
		{
			this.context = context;
			out = new ByteArrayOutputStream();
			buffer = new byte[0x10000];
			resetBuffer();
		}

		void resetBuffer()
		{
			firstRun = true;
			writePos = 0;
		}

		byte[] getAllBytes()
		{
			return out.toByteArray();
		}

		void copyLiterals(RawBytesReader in, int literalsCount, boolean showLiterals) throws ReadException, FormatException
		{
			copyBytes(literalsCount, ()->in.readOneByte(), showLiterals);
		}

		void copyMatch(int offset, int matchLength, boolean showLiterals) throws ReadException, FormatException
		{
			copyBytes(matchLength, ()->getByteFromBuffer(offset), showLiterals);
		}
		
		private interface ReadByte
		{
			int readByte() throws ReadException, FormatException;
		}

		void copyBytes(int length, ReadByte getByte, boolean showLiterals) throws ReadException, FormatException
		{
			for (int i=0; i<length; i++)
			{
				int b = getByte.readByte();
				if (showLiterals) Gui.log(" %02X", b);
				addByte(b);
			}
		}

		private int getByteFromBuffer(int offset) throws FormatException
		{
			if (offset==0)
				throw new FormatException("Wrong offset: Offset==0 (InputReadPos:%d)", context.getReadPos());
			
			int index = writePos - offset;
			
			if (firstRun && index<0)
				throw new FormatException("Wrong offset: Offset (%d) points into range (%d) of unfilled buffer. (BufferWritePos:%d, InputReadPos:%d)", offset, index, writePos, context.getReadPos());
			
			while (index<0)
				index += buffer.length;
			
			return buffer[index];
		}

		private void addByte(int b)
		{
			out.write(b);
			buffer[writePos++] = (byte) b;
			
			while (writePos >= buffer.length)
			{
				firstRun = false;
				writePos -= buffer.length;
			}
		}
	}

	private static class RawBytesReader
	{
		private final byte[] bytes;
		private int readPos;

		RawBytesReader(byte[] bytes)
		{
			this.bytes = Objects.requireNonNull( bytes );
			readPos = 0;
		}

		int getTotalSize() { return            bytes.length; }
		boolean    isEOF() { return readPos >= bytes.length; }
		int       getPos() { return readPos                ; }

		int readOneByte() throws ReadException
		{
			if (readPos+1 > bytes.length)
				throw new ReadException("reading 1 byte goes beyond EndOfFile: readPos:%d, bytes.length:%d", readPos, bytes.length);
			
			return bytes[readPos++] & 0xFF;
		}

		int readTwoByte() throws ReadException
		{
			if (readPos+2 > bytes.length)
				throw new ReadException("reading 2 bytes goes beyond EndOfFile: readPos:%d, bytes.length:%d", readPos, bytes.length);
			
			return
					((bytes[readPos++] & 0xFF) <<  0) |
					((bytes[readPos++] & 0xFF) <<  8);
		}

		int readFourByte() throws ReadException
		{
			if (readPos+4 > bytes.length)
				throw new ReadException("reading 4 bytes goes beyond EndOfFile: readPos:%d, bytes.length:%d", readPos, bytes.length);
			
			return
					((bytes[readPos++] & 0xFF) <<  0) |
					((bytes[readPos++] & 0xFF) <<  8) |
					((bytes[readPos++] & 0xFF) << 16) |
					((bytes[readPos++] & 0xFF) << 24);
		}

		@SuppressWarnings("unused")
		void skip(int n) throws ReadException
		{
			if (readPos+n > bytes.length)
				throw new ReadException("reached EndOfFile");
			
			readPos += n;
		}
	}
	
	private static class ReadException extends Exception
	{
		private static final long serialVersionUID = -3644628680465212593L;

		ReadException(String format, Object... values)
		{
			super(format.formatted(values));
		}
	}
	
	private static class FormatException extends Exception
	{
		private static final long serialVersionUID = 6641514199637112262L;

		FormatException(String format, Object... values)
		{
			super(format.formatted(values));
		}
	}

	@SuppressWarnings("unused")
	private static class Gui
	{
		private static void log_ln      ( PrintStream out, String text                     ) { out.printf( "%s%n", text                        ); }
		private static void log         ( PrintStream out, String text                     ) { out.printf( "%s"  , text                        ); }
		private static void log_ln      ( PrintStream out, String format, Object... values ) { out.printf( Locale.ENGLISH, format+"%n", values ); }
		private static void log         ( PrintStream out, String format, Object... values ) { out.printf( Locale.ENGLISH, format     , values ); }
		public  static void log_ln      ( String text                     ) { log_ln( System.out, text           ); }
		public  static void log         ( String text                     ) { log   ( System.out, text           ); }
		public  static void log_error_ln( String text                     ) { log_ln( System.err, text           ); }
		public  static void log_error   ( String text                     ) { log   ( System.err, text           ); }
		public  static void log_warn_ln ( String text                     ) { log_ln( System.err, text           ); }
		public  static void log_warn    ( String text                     ) { log   ( System.err, text           ); }
		public  static void log_ln      ( String format, Object... values ) { log_ln( System.out, format, values ); }
		public  static void log         ( String format, Object... values ) { log   ( System.out, format, values ); }
		public  static void log_error_ln( String format, Object... values ) { log_ln( System.err, format, values ); }
		public  static void log_error   ( String format, Object... values ) { log   ( System.err, format, values ); }
		public  static void log_warn_ln ( String format, Object... values ) { log_ln( System.err, format, values ); }
		public  static void log_warn    ( String format, Object... values ) { log   ( System.err, format, values ); }
	}
}
