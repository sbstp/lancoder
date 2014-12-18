package org.lancoder.master;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.io.FilenameUtils;
import org.lancoder.common.RunnableService;
import org.lancoder.common.codecs.ChannelDisposition;
import org.lancoder.common.codecs.CodecEnum;
import org.lancoder.common.file_components.FileInfo;
import org.lancoder.common.file_components.streams.AudioStream;
import org.lancoder.common.file_components.streams.VideoStream;
import org.lancoder.common.job.FFmpegPreset;
import org.lancoder.common.job.Job;
import org.lancoder.common.job.RateControlType;
import org.lancoder.common.network.messages.web.ApiJobRequest;
import org.lancoder.common.progress.Unit;
import org.lancoder.common.task.ClientTask;
import org.lancoder.common.task.audio.AudioStreamConfig;
import org.lancoder.common.task.audio.AudioTask;
import org.lancoder.common.task.audio.ClientAudioTask;
import org.lancoder.common.task.video.ClientVideoTask;
import org.lancoder.common.task.video.VideoStreamConfig;
import org.lancoder.common.task.video.VideoTask;
import org.lancoder.common.third_parties.FFprobe;
import org.lancoder.common.utils.FileUtils;
import org.lancoder.ffmpeg.FFmpegWrapper;

public class JobInitiator extends RunnableService {

	private final static String[] EXTENSIONS = new String[] { "mkv", "mp4", "avi", "mov" };

	private final LinkedBlockingDeque<ApiJobRequest> requests = new LinkedBlockingDeque<>();
	private JobInitiatorListener listener;
	private MasterConfig config;

	public JobInitiator(JobInitiatorListener listener, MasterConfig config) {
		this.listener = listener;
		this.config = config;
	}

	public void process(ApiJobRequest request) {
		this.requests.add(request);
	}

	private void createJob(ApiJobRequest req, String jobName, File sourceFile, File outputFolder, File baseOutputFolder) {
		// Get meta-data from source file
		File absoluteFile = FileUtils.getFile(config.getAbsoluteSharedFolder(), sourceFile.getPath());
		FileInfo fileInfo = FFmpegWrapper.getFileInfo(absoluteFile, sourceFile.getPath(), new FFprobe(config));

		FFmpegPreset preset = req.getPreset();
		RateControlType videoRateControlType = req.getRateControlType();
		CodecEnum videoCodec = req.getVideoCodec();
		double requestFrameRate = 0;
		int width = 0;
		int height = 0;

		ArrayList<String> extraEncoderArgs = new ArrayList<>();

		// Limit to max pass from the rate control
		int passes = (req.getPasses() <= videoRateControlType.getMaxPass() ? req.getPasses() : videoRateControlType
				.getMaxPass());
		if (passes <= 0) {
			passes = 1;
		}
		int lengthOfTasks = 1000 * 60 * 5; // TODO get length of task (maybe in an 'advanced section')
		// Audio parameters
		RateControlType audioRCT = null;
		int audioRate = 0;
		int audioSampleRate = 0;
		CodecEnum audioCodec = null;
		ChannelDisposition audioChannels = null;
		switch (req.getAudioPreset()) {
		case COPY:
			audioCodec = CodecEnum.COPY;
			break;
		case AUTO:
			// Set default values
			audioRCT = RateControlType.CRF;
			audioRate = 5;
			audioSampleRate = 48000;
			audioCodec = CodecEnum.VORBIS;
			audioChannels = ChannelDisposition.STEREO;
			break;
		case MANUAL:
			// Set values from user's request
			audioRCT = req.getAudioRateControlType();
			audioCodec = req.getAudioCodec();
			audioChannels = req.getAudioChannels();
			audioSampleRate = req.getAudioSampleRate();
			audioRate = req.getRate();
			break;
		}
		Job job = new Job(jobName, sourceFile.getPath(), lengthOfTasks, fileInfo, outputFolder, baseOutputFolder);

		for (VideoStream stream : fileInfo.getVideoStreams()) {
			double frameRate = requestFrameRate < 1 ? stream.getFrameRate() : requestFrameRate;
			VideoStream streamToEncode = new VideoStream(stream.getIndex(), videoCodec, frameRate, req.getRate(),
					videoRateControlType, preset, width, height, fileInfo.getDuration(), Unit.SECONDS, req.getPasses(),
					stream.getRelativeFile());
			VideoStreamConfig config = new VideoStreamConfig(job.getJobId(), extraEncoderArgs, passes, stream,
					streamToEncode);
			job.addStream(streamToEncode, createTasks(config, job));
		}

		for (AudioStream stream : fileInfo.getAudioStreams()) {
			AudioStream streamToEncode = new AudioStream(stream.getIndex(), audioCodec, stream.getUnitCount(),
					audioRate, audioRCT, audioChannels, audioSampleRate, Unit.SECONDS, stream.getRelativeFile());
			AudioStreamConfig config = new AudioStreamConfig(job.getJobId(), extraEncoderArgs, stream, streamToEncode);
			job.addStream(streamToEncode, createTasks(config, job));
			// TODO Sanitize channel disposition (upmix protection)
			// if (stream.getChannels().getCount() < defaultAudio.getChannels().getCount())
		}
		prepareFileSystem(job);
		listener.newJob(job);
	}

	private void createJob(ApiJobRequest req, String jobName, File sourceFile) {
		File output = FileUtils.getFile(config.getFinalEncodingFolder(), jobName);
		createJob(req, jobName, sourceFile, output, output);
	}

	private ArrayList<ClientTask> createTasks(AudioStreamConfig config, Job job) {
		ArrayList<ClientTask> tasks = new ArrayList<>();
		AudioStream outStream = config.getOutStream();
		if (outStream.getCodec() != CodecEnum.COPY) {
			int taskId = job.getTaskCount();
			File relativeTasksOutput = FileUtils.getFile(job.getPartsFolderName());
			File relativeTaskOutputFile = FileUtils.getFile(relativeTasksOutput,
					String.format("part-%d.%s", taskId, outStream.getCodec().getContainer()));
			AudioTask task = new AudioTask(taskId, job.getJobId(), 0, outStream.getUnitCount(),
					outStream.getUnitCount(), Unit.SECONDS, relativeTaskOutputFile.getPath());
			tasks.add(new ClientAudioTask(task, config));
		}
		return tasks;
	}

	private ArrayList<ClientTask> createTasks(VideoStreamConfig config, Job job) {
		VideoStream outStream = config.getOutStream();
		VideoStream inStream = config.getOrignalStream();
		ArrayList<ClientTask> tasks = new ArrayList<>();
		int taskId = job.getTaskCount();
		// exclude copy streams from task creation
		if (outStream.getCodec() != CodecEnum.COPY) {
			long remaining = 0;
			if (inStream.getUnit() == Unit.FRAMES) {
				remaining = (long) (inStream.getUnitCount() / inStream.getFrameRate());
			} else if (inStream.getUnit() == Unit.SECONDS) {
				// convert from ms to seconds
				remaining = inStream.getUnitCount() * 1000;
			}
			long currentMs = 0;
			File relativeTasksOutput = FileUtils.getFile(job.getPartsFolderName());
			while (remaining > 0) {
				long start = currentMs;
				long end = 0;
				if ((((double) remaining - job.getLengthOfTasks()) / job.getLengthOfJob()) <= 0.15) {
					end = job.getLengthOfJob();
					remaining = 0;
				} else {
					end = currentMs + job.getLengthOfTasks();
					remaining -= job.getLengthOfTasks();
					currentMs += job.getLengthOfTasks();
				}
				String extension = outStream.getCodec() == CodecEnum.H264 ? "mpeg.ts" : outStream.getCodec().getContainer();
				File relativeTaskOutputFile = FileUtils.getFile(relativeTasksOutput,
						String.format("part-%d.%s", taskId, extension));
				long ms = end - start;
				long unitCount = (long) Math.floor((ms / 1000 * outStream.getFrameRate()));
				VideoTask task = new VideoTask(taskId, job.getJobId(), outStream.getStepCount(), start, end, unitCount,
						Unit.FRAMES, relativeTaskOutputFile.getPath());
				tasks.add(new ClientVideoTask(task, config));
				taskId++;
			}
		}
		return tasks;
	}

	private void createJob(ApiJobRequest req, File sourcefile) {
		createJob(req, req.getName(), sourcefile);
	}

	private void processBatchRequest(ApiJobRequest req) {
		File baseSourceFolder = FileUtils.getFile(config.getAbsoluteSharedFolder(), req.getInputFile());
		String globalJobName = req.getName();
		File relGlobalOutput = FileUtils.getFile(config.getFinalEncodingFolder(), globalJobName);
		// clean shared folder if it already exists TODO
		// File sharedParts = new File(config.getFinalEncodingFolder(), "parts");
		// if (sharedParts.exists()) {
		// sharedParts.delete(); // be hard on others
		// }

		// Create all jobs
		Collection<File> toProcess = FileUtils.listFiles(baseSourceFolder, EXTENSIONS, true);
		for (File absoluteFile : toProcess) {
			File relativeJobFile = new File(relativize(absoluteFile));
			String fileName = FilenameUtils.removeExtension(relativeJobFile.getName());
			URI jobOutputUri = baseSourceFolder.toURI().relativize(absoluteFile.getParentFile().toURI());
			File jobOutput = new File(relGlobalOutput, jobOutputUri.getPath());
			String jobName = String.format("%s - %s ", globalJobName, fileName);
			createJob(req, jobName, relativeJobFile, jobOutput, relGlobalOutput);
		}
	}

	/**
	 * Relativize file from shared directory
	 * 
	 * @param file
	 *            The absolute file
	 * @return The relative representation of the file
	 */
	private String relativize(File file) {
		URI uri = new File(config.getAbsoluteSharedFolder()).toURI().relativize(file.toURI());
		return uri.getPath();
	}

	private void processJobRequest(ApiJobRequest req) {
		String relativeSourceFile = req.getInputFile();
		File absoluteSourceFile = new File(config.getAbsoluteSharedFolder(), relativeSourceFile);
		if (absoluteSourceFile.isDirectory()) {
			processBatchRequest(req);
		} else {
			createJob(req, new File(relativeSourceFile));
		}
	}

	private void prepareFileSystem(Job j) {
		// Create base folders
		File absoluteOutput = FileUtils.getFile(config.getAbsoluteSharedFolder(), j.getOutputFolder());
		// TODO replace with file path manager
		File absolutePartsOutput = FileUtils.getFile(config.getAbsoluteSharedFolder(), j.getPartsFolderName());
		if (!absoluteOutput.exists()) {
			absoluteOutput.mkdirs();
			FileUtils.givePerms(absoluteOutput, false);
		}
		absolutePartsOutput.mkdirs();
		FileUtils.givePerms(absoluteOutput, true);
	}

	@Override
	public void run() {
		try {
			while (!close) {
				processJobRequest(requests.take());
			}
		} catch (InterruptedException e) {
		}
	}

	@Override
	public void serviceFailure(Exception e) {
		// TODO Auto-generated method stub
	}
}
