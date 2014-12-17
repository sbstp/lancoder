package org.lancoder.worker.converter;

import java.io.File;
import java.io.IOException;

import org.lancoder.common.FilePathManager;
import org.lancoder.common.pool.Pooler;
import org.lancoder.common.task.ClientTask;
import org.lancoder.common.third_parties.FFmpeg;
import org.lancoder.common.utils.FileUtils;
import org.lancoder.ffmpeg.FFmpegReaderListener;

public abstract class Converter<T extends ClientTask> extends Pooler<T> implements FFmpegReaderListener {

	protected ConverterListener listener;
	protected FilePathManager filePathManager;
	protected FFmpeg ffMpeg;

	/**
	 * Constructor of base converter. Initialize file names and directories from task configuration.
	 * 
	 * @param task
	 *            The ClientTask containing global task config.
	 */
	public Converter(ConverterListener listener, FilePathManager filePathManager, FFmpeg fFmpeg) {
		super();
		this.listener = listener;
		this.filePathManager = filePathManager;
		this.ffMpeg = fFmpeg;
	}

	@Override
	public abstract void cancelTask(Object task);

	/**
	 * Create or clean task's and job's folders.
	 */
	protected void createDirs() {
		// Create task folder on absolute share
		File sharedFolder = filePathManager.getSharedFinalFile(task).getParentFile();
		if (!sharedFolder.exists()) {
			sharedFolder.mkdirs();
			FileUtils.givePerms(sharedFolder, false);
		}
		// Create temporary task folder on local file system (also creates job's folder)
		File localFolder = filePathManager.getLocalTempFolder(task);
		if (!localFolder.exists()) {
			localFolder.mkdirs();
			FileUtils.givePerms(localFolder, false);
		} else {
			// Remove any previous temporary files for this part (on local FS)
			cleanTempFolder();
		}
	}

	/**
	 * Clean task's temporary folder.
	 */
	private void cleanTempFolder() {
		System.out.println("WORKER: Cleaning temp task folder content.");
		File localFolder = filePathManager.getLocalTempFolder(task);
		if (localFolder.isDirectory()) {
			try {
				FileUtils.cleanDirectory(localFolder);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Destroy task's temporary folder and job's (parent) temporary folder if empty.
	 */
	protected void destroyTempFolder() {
		File localTaskFolder = filePathManager.getLocalTempFolder(task);
		File localJobFolder = localTaskFolder.getParentFile();

		// Delete local temp task folder
		cleanTempFolder();
		localTaskFolder.delete();

		// Delete local temp job folder if empty
		if (localJobFolder.list().length == 0) {
			localJobFolder.delete();
		}
	}
}
