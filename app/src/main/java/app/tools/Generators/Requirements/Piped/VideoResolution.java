/*
 * SkyTube
 * Copyright (C) 2015  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package app.tools.Generators.Requirements.Piped;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.tools.StaticFunctions;

/**
 * Video resolution (e.g. 1080p).
 */
public enum VideoResolution {

	//			(id, vertical pixels, itags list)
	/** Unknown video resolution */
	Audio(-1, -1),
	_144P(0, 144),
	_240P(1, 240),
	_360P(2, 360),
	_480P(3, 480),
	/** 720p - HD */
	_720P(4, 720),
	/** 1080p - HD */
	_1080P(5, 1080);

	// these will be added eventually
	/** 1440p - HD */
	//RES_1440P   (6, 1440),
	/** 2160p - 4k */
	//RES_2160P (7, 2160);
	/** 4320p - 8k */
	//RES_4320P;

	/** Video resolution ID */
	private final int id;
	/** Number of vertical pixels this video resolution has (e.g. 1080) */
	private final int verticalPixels;

	/**
	 * The default video resolution (ID) that will be used if the user has not choose a desired
	 * one.
	 */
	public  static final int	DEFAULT_VIDEO_RES_ID = _1080P.id;
	private static final String TAG = VideoResolution.class.getSimpleName();
	private static final Pattern NUMBERS = Pattern.compile("[0-9]*");


	VideoResolution(int id, int verticalPixels) {
		this.id = id;
		this.verticalPixels = verticalPixels;
	}

	@Override
	public String toString() {
		return verticalPixels + "p";
	}


	/**
	 * Returns a {@link VideoResolution} that is next-step lower than the current one.
	 *
	 * @return A lower {@link VideoResolution}.
	 */
	public VideoResolution getLowerVideoResolution() {
		if (this == Audio)
			return Audio;

		VideoResolution[] resList = VideoResolution.values();
		return resList[this.id];
	}


	public boolean isBetterQualityThan(VideoResolution other) {
		return this.ordinal() > other.ordinal();
	}

	public boolean isLessNetworkUsageThan(VideoResolution other) {
		return this != Audio && this.ordinal() < other.ordinal();
	}

	public static VideoResolution resolutionToVideoResolution(String resolution) {
		VideoResolution[] resList = VideoResolution.values();

		Matcher matcher = NUMBERS.matcher(resolution);
		if (matcher.find()) {
			final int verticalPixel = Integer.parseInt(matcher.group());
			for (VideoResolution res : resList) {
				if (res.verticalPixels == verticalPixel) {
					return res;
				}
			}
		}

		return Audio;
	}


	/**
	 * Converts the ID of a {@link VideoResolution} to an instance of {@link VideoResolution}.
	 *
	 * @param resIdString Video resolution ID
	 * @return {@link VideoResolution}
	 */
	public static VideoResolution videoResIdToVideoResolution(String resIdString) {
		if (resIdString == null) {
			return VideoResolution.Audio;
		}
		VideoResolution[] resList = VideoResolution.values();
		int resId = Integer.parseInt(resIdString);

		for (VideoResolution res : resList) {
			if (res.id == resId)
				return res;
		}

		return Audio;
	}


	/**
	 * Returns a list of video resolutions IDs.
	 *
	 * @return List of {@link String}.
	 */
	public static String[] getAllVideoResolutionsIds() {
		VideoResolution[] resList = VideoResolution.values();
		String[] resStringList = new String[resList.length];

		for (int i = 0;  i < resList.length;  i++) {
			resStringList[i] = Integer.toString(resList[i].id);
		}

		return resStringList;
	}
	/**
	 * Returns a list of video resolutions Keys.
	 *
	 * @return List of {@link String}.
	 */
	public static String[] getAllVideoResolutionsKeys() {
		VideoResolution[] resList = VideoResolution.values();
		String[] resStringList = new String[resList.length];

		resStringList[0] = StaticFunctions.asJsonFormat("Audio Only");

		for (int i = 1;  i < resList.length;  i++) {
			resStringList[i] = StaticFunctions.asJsonFormat(resList[i].toString());
		}

		return resStringList;
	}
}
