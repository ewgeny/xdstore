package org.flib.xdstore.serialization;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

public class ConsoleWriter extends Writer {

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		System.out.print(Arrays.copyOfRange(cbuf, off, off + len));
	}

	@Override
	public void flush() throws IOException {
		System.out.flush();
	}

	@Override
	public void close() throws IOException {
		System.out.close();
	}

}
