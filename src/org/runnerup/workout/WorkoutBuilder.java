/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.workout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.runnerup.R;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.view.AudioCueSettingsActivity;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.feedback.AudioCountdownFeedback;
import org.runnerup.workout.feedback.AudioFeedback;
import org.runnerup.workout.feedback.CoachFeedback;
import org.runnerup.workout.feedback.CountdownFeedback;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.format.DateUtils;

public class WorkoutBuilder {

	/**
	 * @return workout based on SharedPreferences
	 */
	public static Workout createDefaultWorkout(Resources res, SharedPreferences prefs,
			boolean targetPace) {
		Workout w = new Workout();
		w.sport = prefs.getInt("basicSport", DB.ACTIVITY.SPORT_RUNNING);

		if (prefs.getBoolean(res.getString(R.string.pref_countdown_active), false))
		{
			long val = 0;
			String vals = prefs.getString(res.getString(R.string.pref_countdown_time), "0");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				Step step = Step.createPauseStep(Dimension.TIME, val);
				w.steps.add(step);
			}
		}

		Step step = new Step();
		if (prefs.getBoolean(res.getString(R.string.pref_autolap_active), false)) {
			double val = 0;
			String vals = prefs.getString(res.getString(R.string.pref_autolap), "1000");
			try {
				val = Double.parseDouble(vals);
			} catch (NumberFormatException e) {
			}
			step.setAutolap(val);
		}

		w.steps.add(step);

		if (targetPace)
		{
			double unitMeters = Formatter.getUnitMeters(prefs);
			double seconds_per_unit = (double)SafeParse.parseSeconds(prefs.getString("basic_target_pace_max", "00:05:00"), 5*60);
			int targetPaceRange = prefs.getInt("basic_target_pace_min_range", 15);
			double targetPaceMax = seconds_per_unit / unitMeters;
			double targetPaceMin = (targetPaceMax * unitMeters - targetPaceRange) / unitMeters;
			Range range = new Range(targetPaceMin, targetPaceMax);
			step.targetType = Dimension.PACE;
			step.targetValue = range;
		}
		
		/**
		 *
		 */
		return w;
	}
	
	public static Workout createDefaultIntervalWorkout(Resources res, SharedPreferences prefs) {
		Workout w = new Workout();
		final boolean warmup = true;
		final boolean cooldown = true;

		if (warmup) {
			Step step = new Step();
			step.intensity = Intensity.WARMUP;
			step.durationType = null;
			w.steps.add(step);
		}

		int repetitions = (int) SafeParse.parseDouble(prefs.getString("intervalRepetitions", "1"), 1);
		
		int intervalType = prefs.getInt("intervalType", 0);
		long intervalTime = SafeParse.parseSeconds(prefs.getString("intervalTime", "00:04:00"), 4 * 60);
		double intevalDistance = SafeParse.parseDouble(prefs.getString("intervalDistance", "1000"), 1000);
		int intervalRestType = prefs.getInt("intervalRestType", 0);
		long intervalRestTime = SafeParse.parseSeconds(prefs.getString("intervalRestTime", "00:01:00"), 60);
		double intevalRestDistance = SafeParse.parseDouble(prefs.getString("intervalRestDistance", "200"), 200);
		
		RepeatStep repeat = new RepeatStep();
		repeat.repeatCount = repetitions;
		{
			Step step = new Step();
			switch (intervalType) {
			case 0: // Time
				step.durationType = Dimension.TIME;
				step.durationValue = intervalTime;
				break;
			case 1: // Distance
				step.durationType = Dimension.DISTANCE;
				step.durationValue = intevalDistance;
				break;
			}
			repeat.steps.add(step);

			if (true) {
				Step rest = null;
				switch (intervalRestType) {
				case 0: // Time
					rest = Step.createPauseStep(Dimension.TIME, intervalRestTime);
					break;
				case 1: // Distance
					rest = Step.createPauseStep(Dimension.DISTANCE, intevalRestDistance);
					break;
				}
				repeat.steps.add(rest);
			}
		}
		w.steps.add(repeat);
		
		if (cooldown) {
			Step step = new Step();
			step.intensity = Intensity.COOLDOWN;
			step.durationType = null;
			w.steps.add(step);
		}
		
		return w;
	}
	
	public static boolean validateSeconds(String newValue) {
		//TODO move this somewhere
		long seconds = SafeParse.parseSeconds(newValue, -1);
		long seconds2 = SafeParse.parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
		if (seconds == seconds2)
			return true;
		return false;
	}

	public static SharedPreferences getAudioCuePreferences(Context ctx, SharedPreferences pref, String key) {
		return getSubPreferences(ctx, pref, key, AudioCueSettingsActivity.DEFAULT, AudioCueSettingsActivity.SUFFIX);
	}

	public static SharedPreferences getSubPreferences(Context ctx, SharedPreferences pref,
			String key, String defaultVal, String suffix) {
		String name = pref.getString(key, null);
		if (name == null || name.contentEquals(defaultVal)) {
			return pref;
		}
		return ctx.getSharedPreferences(name + suffix, Context.MODE_PRIVATE);
	}

	public static void addAudioCuesToWorkout(Resources res, Workout w, SharedPreferences prefs) {
		w.setAudioCues(prefs);
		addAudioCuesToWorkout(res, w.steps, prefs);
	}

	private static void addAudioCuesToWorkout(Resources res, ArrayList<Step> steps, SharedPreferences prefs) {
		final boolean skip_startstop_cue = prefs.getBoolean(res.getString(R.string.cueinfo_skip_startstop), false);
		ArrayList<Trigger> triggers = createDefaultTriggers(res, prefs);
		boolean silent = triggers.size() == 0;
		final boolean coaching = prefs.getBoolean(res.getString(R.string.cueinfo_target_coaching), true);
		if (silent && coaching) {
			for (Step s : steps) {
				if (s.getTargetType() != null) {
					silent = false;
					break;
				}
			}
		}
		
		addPauseStopResumeTriggers(res, triggers, prefs);
		if (!silent)
		{
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.STEP;
			ev.maxCounter = 1;
			ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			triggers.add(ev);
			
			EventTrigger ev2 = new EventTrigger(); // for autolap
			ev2.event = Event.STARTED;
			ev2.scope = Scope.LAP;
			ev2.skipCounter = 1; // skip above
			ev2.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			triggers.add(ev2);
		}
		
		Step stepArr[] = new Step[steps.size()];
		steps.toArray(stepArr);
		for (int i = 0 ; i < stepArr.length; i++) {
			// Step prev = i == 0 ? null : stepArr[i-1];
			Step step = stepArr[i];
			Step next = i + 1 == stepArr.length ? null : stepArr[i+1];
			switch(step.getIntensity()) {
			case REPEAT:
				addAudioCuesToWorkout(res, ((RepeatStep)step).steps, prefs);
				break;
			case ACTIVE:
				step.triggers.addAll(triggers);
				if (!silent && (next == null || next.getIntensity() != step.getIntensity()))
				{
					EventTrigger ev = new EventTrigger();
					ev.event = Event.COMPLETED;
					ev.scope = Scope.STEP;
					ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.COMPLETED));
					step.triggers.add(ev);
				}
				break;
			case RESTING: {
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = step.durationType;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.STEP;
				trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, step.durationType));
				step.triggers.add(trigger);
				addPauseStopResumeTriggers(res, step.triggers, prefs);

				if (!silent) {
					createAudioCountdown(step);
				}
				break;
			}
			case WARMUP:
			case COOLDOWN:
				addPauseStopResumeTriggers(res, step.triggers, prefs);
				if (skip_startstop_cue == false) {
					EventTrigger ev = new EventTrigger();
					ev.event = Event.STARTED;
					ev.scope = Scope.STEP;
					ev.triggerAction.add(new AudioFeedback(step.getIntensity(), Event.STARTED));
					step.triggers.add(ev);
				}
				break;
			}

			if (coaching && step.getTargetType() != null) {
				Range range = step.getTargetValue();
				int averageSeconds = SafeParse.parseInt(prefs.getString(res.getString(R.string.target_pace_moving_average_seconds), "20"), 20);
				int graceSeconds = SafeParse.parseInt(prefs.getString(res.getString(R.string.target_pace_grace_seconds), "30"), 30);
				TargetTrigger tr = new TargetTrigger(averageSeconds, graceSeconds);
				tr.scope = Scope.STEP;
				tr.dimension = step.getTargetType();
				tr.range = range;
				tr.triggerAction.add(new CoachFeedback(Scope.WORKOUT, step.getTargetType(), range, tr));
				step.triggers.add(tr);
			}
		}
	}

	private static ArrayList<Trigger> createDefaultTriggers(Resources res, SharedPreferences prefs) {
		ArrayList<Feedback> feedback = new ArrayList<Feedback>();
		ArrayList<Trigger> triggers = new ArrayList<Trigger>();

		if (prefs.getBoolean(res.getString(R.string.cue_time), false)) {
			long val = 0;
			String vals = prefs.getString(res.getString(R.string.cue_time_intervall), "120");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				IntervalTrigger t = new IntervalTrigger();
				t.first = val;
				t.interval = val;
				t.scope = Scope.STEP;
				t.dimension = Dimension.TIME;
				triggers.add(t);
			}
		}
		
		if (prefs.getBoolean(res.getString(R.string.cue_distance), false)) {
			long val = 0;
			String vals = prefs.getString(res.getString(R.string.cue_distance_intervall), "1000");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				IntervalTrigger t = new IntervalTrigger();
				t.first = val;
				t.interval = val;
				t.scope = Scope.STEP;
				t.dimension = Dimension.DISTANCE;
				triggers.add(t);
			}
		}

		if (prefs.getBoolean(res.getString(R.string.cueinfo_total_distance), false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.DISTANCE));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_total_time), false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.TIME));
		}
		if (Dimension.SPEED_CUE_ENABLED && prefs.getBoolean(res.getString(R.string.cueinfo_total_speed), false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.SPEED));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_total_pace), false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.PACE));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_step_distance), false)) {
			feedback.add(new AudioFeedback(Scope.STEP, Dimension.DISTANCE));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_step_time), false)) {
			feedback.add(new AudioFeedback(Scope.STEP, Dimension.TIME));
		}
		if (Dimension.SPEED_CUE_ENABLED && prefs.getBoolean(res.getString(R.string.cueinfo_step_speed), false)) {
			feedback.add(new AudioFeedback(Scope.STEP, Dimension.SPEED));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_step_pace), false)) {
			feedback.add(new AudioFeedback(Scope.STEP, Dimension.PACE));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_distance), false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.DISTANCE));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_time), false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.TIME));
		}
		if (Dimension.SPEED_CUE_ENABLED && prefs.getBoolean(res.getString(R.string.cueinfo_lap_speed), false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.SPEED));
		}
		if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_pace), false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.PACE));
		}

		for (Trigger t : triggers) {
			t.triggerAction = feedback;
		}

		return triggers;
	}
	
	private static void addPauseStopResumeTriggers(Resources res, ArrayList<Trigger> list, SharedPreferences prefs) {
		if (prefs.getBoolean(res.getString(R.string.cueinfo_skip_startstop), false) == false) {
			{
				EventTrigger p = new EventTrigger();
				p.event = Event.PAUSED;
				p.scope = Scope.STEP;
				p.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.PAUSED));
				list.add(p);
			}

			{
				EventTrigger r = new EventTrigger();
				r.event = Event.RESUMED;
				r.scope = Scope.STEP;
				r.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.RESUMED));
				list.add(r);
			}

			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STOPPED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.STOPPED));
				list.add(ev);
			}
		}
	}	

	private static void createAudioCountdown(Step step) {
		double first = 0;
		ArrayList<Double> list = new ArrayList<Double>();
		switch (step.getDurationType()) {
		case TIME:
			first = 60; // 1 minute
			Double tmp0[] = { 60d, 30d, 10d, 5d, 3d, 2d, 1d };
			list.addAll(Arrays.asList(tmp0));
			break;
		case DISTANCE:
			first = 100; // 100 meters
			Double tmp1[] ={ 100d, 50d, 20d, 10d }; 
			list.addAll(Arrays.asList(tmp1));
			break;
		default:
			return;
		}

		if (step.getDurationValue() > first) {
			/**
			 * If longer than limit...create a Interval trigger for ">" part
			 */
			IntervalTrigger trigger = new IntervalTrigger();
			trigger.dimension = step.getDurationType();
			trigger.scope = Scope.STEP;
			trigger.first = first;
			trigger.interval = first;
			trigger.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(trigger);
		}

		/**
		 * Then create a list trigger for reminder...
		 */
		ArrayList<Double> triggerTimes = new ArrayList<Double>();
		for (Double d : list) {
			if (d >= step.getDurationValue())
				continue;
			double val = step.getDurationValue() - d;
			if ((val % first) == 0) {
				continue; // handled by interval trigger
			}
			double margin = 0.4d; // add a bit of margin, NOTE: less than 0.5
			triggerTimes.add(d + margin);
		}
		
		{
			ListTrigger trigger = new ListTrigger();
			trigger.remaining = true;
			trigger.dimension = step.getDurationType();
			trigger.scope = Scope.STEP;
			trigger.triggerTimes = triggerTimes;
			trigger.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(trigger);
		}

		if (true) {
			/**
			 * Add add information just when pause step starts...
			 */
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.STEP;
			ev.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(ev);
		}
	}	

	public static void prepareWorkout(Resources res, SharedPreferences prefs, Workout w, boolean basic) {
		List<StepListEntry> steps = w.getSteps();
		
		/**
		 * Add/remove autolap
		 */
		boolean autolap = prefs.getBoolean(res.getString(R.string.pref_autolap_active), false);
		if (basic == false) {
			autolap = prefs.getBoolean(res.getString(R.string.pref_step_autolap_active), autolap);
		}
		if (autolap) {
			double val = 0;
			String vals = prefs.getString(res.getString(R.string.pref_autolap), "1000");
			try {
				val = Double.parseDouble(vals);
			} catch (NumberFormatException e) {
			}
			System.out.println("setAutolap("+val+")");
			for (StepListEntry s : steps) {
				s.step.setAutolap(0); // reset
				switch(s.step.getIntensity()) {
				case ACTIVE:
					s.step.setAutolap(val);
					break;
				case RESTING:
				case WARMUP:
				case COOLDOWN:
				case REPEAT:
					break;
				}
			}
		}

		/**
		 * Add countdowns after steps with duration "until pressed", if next is not a countdown
		 */
		if (prefs.getBoolean(res.getString(R.string.pref_step_countdown_active), true))
		{
			long val = 15; // default 15s
			String vals = prefs.getString(res.getString(R.string.pref_step_countdown_time), "15");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				StepListEntry stepArr[] = new StepListEntry[steps.size()];
				steps.toArray(stepArr);
				for (int i = 0; i < stepArr.length; i++) {
					// Step prev = i == 0 ? null : stepArr[i-1];
					Step step = stepArr[i].step;
					Step next = i + 1 == stepArr.length ? null : stepArr[i+1].step;
					
					if (step.durationType != null)
						continue;
					
					if (step.intensity == Intensity.REPEAT)
						continue;
					
					if (next == null)
						continue;
					
					if (next.durationType == Dimension.TIME && next.intensity == Intensity.RESTING)
						continue;
					
					Step s = Step.createPauseStep(Dimension.TIME, val);
					if (stepArr[i].parent == null) {
						w.steps.add(i + 1, s);
						System.err.println("Added step at index: " + (i + 1));
					} else {
						RepeatStep rs = (RepeatStep) stepArr[i].parent;
						int idx = rs.steps.indexOf(step);
						rs.steps.add(idx, s);
						System.err.println("Added step at index: " + (i + 1) + " repeat index: " + (idx + 1));
					}
				}
			}
		}
	}
}
