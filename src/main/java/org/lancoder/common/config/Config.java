package org.lancoder.common.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.lancoder.common.annotations.Prompt;
import org.yaml.snakeyaml.Yaml;

public abstract class Config {

	private static final String DEFAULT_FFMPEG_PATH = "ffmpeg";
	private static final String DEFAULT_ABSOLUTE_PATH = System.getProperty("user.home");
	private static final String DEFAULT_TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");

	protected transient String configPath;

	@Prompt(message = "shared folder root", priority = 10)
	protected String absoluteSharedFolder;

	@Prompt(message = "FFmpeg's path", priority = 20)
	protected String ffmpegPath;

	@Prompt(message = "temporary files location", priority = 20, advanced = true)
	protected String tempEncodingFolder;

	protected Config() {
		this.ffmpegPath = DEFAULT_FFMPEG_PATH;
		this.tempEncodingFolder = DEFAULT_TEMP_DIRECTORY;
		this.absoluteSharedFolder = DEFAULT_ABSOLUTE_PATH;
	}

	/**
	 * Serializes current config to disk as YAML object.
	 * 
	 * @return True if could write config to disk. Otherwise, return false.
	 */
	@Deprecated
	public synchronized boolean dump() {
		File config = new File(configPath);
		Yaml yaml = new Yaml();
		String s = yaml.dumpAsMap(this);
		try {
			if (!config.exists()) {
				config.getParentFile().mkdirs();
			}
			Files.write(Paths.get(configPath), s.getBytes("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void setAbsoluteSharedFolder(String absoluteSharedFolder) {
		this.absoluteSharedFolder = absoluteSharedFolder;
	}

	public String getFfmpegPath() {
		return ffmpegPath;
	}

	public void setFfmpegPath(String ffmpegPath) {
		this.ffmpegPath = ffmpegPath;
	}

	@Override
	public String toString() {
		return String.format("Shared folder location: %s.%n", this.getAbsoluteSharedFolder());
	}

	public String getTempEncodingFolder() {
		return tempEncodingFolder;
	}

	public void setTempEncodingFolder(String tempEncodingFolder) {
		this.tempEncodingFolder = tempEncodingFolder;
	}

	public String getAbsoluteSharedFolder() {
		return absoluteSharedFolder;
	}

	public abstract String getDefaultPath();

	public String getFFmpegPath() {
		return ffmpegPath;
	}
}
