package org.lancoder.ffmpeg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

import org.lancoder.common.ServiceAdapter;
import org.lancoder.common.exceptions.MissingThirdPartyException;

/**
 * Class to create a process, read its output and provide information about it's closing state.
 * 
 *
 */
public class FFmpegReader extends ServiceAdapter {

	Process p;
	FFmpegReaderListener listener;

	/**
	 * Create a process and read the desired output. Interface's onMessage method is called on each line.
	 * 
	 * @param args
	 *            The arguments of the command line
	 * @param listener
	 *            The listener that will read the lines
	 * @param useStdErr
	 *            True to read from stderr, false to read from stdout
	 * @return true if FFmpeg exited cleanly
	 * @throws MissingThirdPartyException
	 */
	public boolean read(ArrayList<String> args, FFmpegReaderListener listener, boolean useStdErr)
			throws MissingThirdPartyException {
		return read(args, listener, useStdErr, null);
	}

	/**
	 * Create a process in the specified directory and read the desired output. Interface's onMessage method is called
	 * on each line.
	 * 
	 * @param args
	 *            The arguments of the command line
	 * @param listener
	 *            The listener that will read the lines
	 * @param useStdErr
	 *            True to read from stderr, false to read from stdout
	 * @param processDirectory
	 *            The directory to execute the process in
	 * @return true if FFmpeg exited cleanly
	 * @throws MissingThirdPartyException
	 */
	public boolean read(ArrayList<String> args, FFmpegReaderListener listener, boolean useStdErr, File processDirectory)
			throws MissingThirdPartyException {
		this.listener = listener;
		boolean success = false;
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.directory(processDirectory);
		 System.out.println(pb.command().toString()); // DEBUG
		Scanner s = null;
		try {
			p = pb.start();
			InputStream stream = useStdErr ? p.getErrorStream() : p.getInputStream();
			s = new Scanner(stream);
			while (s.hasNext() && !close) {
				listener.onMessage(s.nextLine());
			}
			success = p.waitFor() == 0 ? true : false;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
		} finally {
			if (close && p != null) {
				p.destroy();
			}
			if (s != null) {
				s.close();
			}
		}
		return success;
	}

	@Override
	public void stop() {
		super.stop();
		if (p != null) {
			p.destroy();
		}
	}
}
