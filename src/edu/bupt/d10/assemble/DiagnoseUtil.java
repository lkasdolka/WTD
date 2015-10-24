package edu.bupt.d10.assemble;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.plaf.metal.MetalIconFactory.FileIcon16;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import edu.bupt.d10.original.ChartTool;
import edu.bupt.d10.original.Test;

public class DiagnoseUtil implements ParamIndex {

	private static final int TEM_MIN_RANGE = 10 * 60;
	private static final int ENTIRE_WT_DIMENSION = 30;
	private static final double WARNING_MEAN_DEVIATION = 0.05;
	private static final int TRANS_SYS_DIMENSION = 4;
	private static final int PITCH_SYS_DIMENSION = 15;
	private static final int ELECTRIC_SYS_DIMENSION = 8;
	private static final double PARTIAL_FAULT_LIMIT = 0.1;
	private static final String TEST_FILE_OK = "B_3.csv";
	private static final String TEST_FILE_NOT_OK = "C_1.csv";
	private static final int DATA_INTERVAL = 30;
	private static final int DATA_DIMENSION_NUM = 33;
	private static final double EPSILON = 0.00001;
	private static final int DRAWING_IMAGE_POINTS_NUMBER = 500;

	private static final String WHOLE_WT_CHART = "Whole Wind Turbine";
	private static final String WT_TRANS_SYS_CHART = "Transmission System";
	private static final String WT_PITCH_SYS_CHART = "Pitch System";
	private static final String WT_ELECTRIC_SYS_CHART = "Electrical System";

	private static final int MAX_TRAIN_POINT_COUNT = 1000000; // 10^6
	private static final int DATA_NUM_PER_DAY = 60 * 60 * 24;
	private static final String MODEL_DIR = "c:\\diagnose_model";

	/*
	 * 2015.10.24 data file uri: dir/machineName/date, date>= begindate, date<=
	 * enddate
	 * 
	 * ①if data point number > data number needed to train the model, use
	 * uniform interval to pick required number of data points
	 */
	public void train(final String beginDate, final String endDate,
			String machineName, String dir) {
		String machineDir = dir + File.separator + machineName;
		File machineDirFile = new File(machineDir);
		int readDataInterval = 0;
		int dataNumPerFile = 0;
		int trainingSetDataNum = 0;
		boolean readOneByOne = true;
		File[] demandedFiles = machineDirFile.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.toString().compareTo(beginDate) >= 0
						&& pathname.toString().compareTo(endDate) <= 0) {
					return true;
				}
				return false;
			}
		});

		if (demandedFiles == null || demandedFiles.length == 0) {
			System.out.println("no satisfied file, return. ");
			return;
		}

		int demandFileNum = demandedFiles.length;
		if (demandFileNum * DATA_NUM_PER_DAY > MAX_TRAIN_POINT_COUNT) {
			trainingSetDataNum = MAX_TRAIN_POINT_COUNT;
			readDataInterval = demandFileNum * DATA_NUM_PER_DAY
					/ MAX_TRAIN_POINT_COUNT;
			dataNumPerFile = DATA_NUM_PER_DAY / readDataInterval;
			readOneByOne = false;
			System.out.println("readDataInterval:" + readDataInterval
					+ "\ndataNumPerFile:" + dataNumPerFile);
		} else {
			trainingSetDataNum = demandFileNum * DATA_NUM_PER_DAY;
			readDataInterval = 1;
			readOneByOne = true;
		}

		/*
		 * Define models
		 */
		Problem entireWT = new Problem(); // 风机整体
		Problem transmissionSys = new Problem(); // 传动系统：齿轮箱、轴承
		Problem pitchSys = new Problem(); // 变桨系统：桨叶、轮毂、液压系统、油箱、伺服器等
		Problem electricSys = new Problem(); // 发电系统：发电机

		/*
		 * Define model feature parameters
		 */

		/*
		 * problem.l number of training examples：训练样本数 problem.n number of
		 * features：特征维数 problem.x feature nodes：特征数据 problem.y target
		 * values：类别/输出值
		 */

		/*
		 * l marks training set size n marks training data feature dimension x
		 * marks input y marks output
		 */
		entireWT.l = trainingSetDataNum;
		transmissionSys.l = trainingSetDataNum;
		pitchSys.l = trainingSetDataNum;
		electricSys.l = trainingSetDataNum;

		entireWT.n = 30;
		transmissionSys.n = 4;
		pitchSys.n = 15;
		electricSys.n = 8;

		/* X is input */
		Feature[][] entireWT_X = new FeatureNode[trainingSetDataNum][];
		Feature[][] transmissionSys_X = new FeatureNode[trainingSetDataNum][];
		Feature[][] pitchSys_X = new FeatureNode[trainingSetDataNum][];
		Feature[][] electricSys_X = new FeatureNode[trainingSetDataNum][];

		/* Y is output */
		double[] entireWT_Y = new double[trainingSetDataNum];
		double[] transmissionSys_Y = new double[trainingSetDataNum];
		double[] pitchSys_Y = new double[trainingSetDataNum];
		double[] electricSys_Y = new double[trainingSetDataNum];

		BufferedReader reader = null;
		String line = null;
		int dataRowNum = -1;

		try {
			for (File candidateFile : demandedFiles) {
				String candidateFileUri = machineDir + File.separator
						+ candidateFile;
				reader = new BufferedReader(new FileReader(candidateFileUri));
				// skip the first line
				reader.readLine();

				while (dataRowNum < trainingSetDataNum
						&& (line = reader.readLine()) != null) {
					dataRowNum++;
					String[] item = line.split(",");
					System.out.println("item's length:" + item.length);

					/* train a row for entire wt */
					Feature[] entireWT_ROW = new FeatureNode[] {
							new FeatureNode(
									1,
									Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
							new FeatureNode(
									2,
									Double.parseDouble(item[generator_bearing_temperature_a_index])),
							new FeatureNode(
									3,
									Double.parseDouble(item[generator_bearing_temperature_b_index])),
							new FeatureNode(4,
									Double.parseDouble(item[grid_I1_index])),
							new FeatureNode(5,
									Double.parseDouble(item[grid_I2_index])),
							new FeatureNode(6,
									Double.parseDouble(item[grid_I3_index])),
							new FeatureNode(7,
									Double.parseDouble(item[grid_UL1_index])),
							new FeatureNode(8,
									Double.parseDouble(item[grid_UL2_index])),
							new FeatureNode(9,
									Double.parseDouble(item[grid_UL3_index])),
							new FeatureNode(
									10,
									Double.parseDouble(item[main_bearing_gearbox_side_temperature_index])), // 10th
							new FeatureNode(
									11,
									Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
							new FeatureNode(
									12,
									Double.parseDouble(item[nacelle_temperature_index])),
							new FeatureNode(
									13,
									Double.parseDouble(item[nacelle_vibration_effective_value_index])),
							new FeatureNode(
									14,
									Double.parseDouble(item[nacelle_vibration_sensor_momentary_offset_max_index])),
							new FeatureNode(
									15,
									Double.parseDouble(item[nacelle_vibration_sensor_x_index])),
							new FeatureNode(
									16,
									Double.parseDouble(item[nacelle_vibration_sensor_y_index])),
							new FeatureNode(
									17,
									Double.parseDouble(item[pitch_drive_current_1_index])),
							new FeatureNode(
									18,
									Double.parseDouble(item[pitch_drive_current_2_index])),
							new FeatureNode(
									19,
									Double.parseDouble(item[pitch_drive_current_3_index])),
							new FeatureNode(
									20,
									Double.parseDouble(item[pitch_position_1_index])), // 20th
							new FeatureNode(
									21,
									Double.parseDouble(item[pitch_position_2_index])),
							new FeatureNode(
									22,
									Double.parseDouble(item[pitch_position_3_index])),
							new FeatureNode(
									23,
									Double.parseDouble(item[pitch_ssb_motor_current_1_index])),
							new FeatureNode(
									24,
									Double.parseDouble(item[pitch_ssb_motor_current_2_index])),
							new FeatureNode(
									25,
									Double.parseDouble(item[pitch_ssb_motor_current_3_index])),
							new FeatureNode(
									26,
									Double.parseDouble(item[pitch_ssb_motor_temperature_1_index])),
							new FeatureNode(
									27,
									Double.parseDouble(item[pitch_ssb_motor_temperature_2_index])),
							new FeatureNode(
									28,
									Double.parseDouble(item[pitch_ssb_motor_temperature_3_index])),
							new FeatureNode(29,
									Double.parseDouble(item[rotor_speed_index])),
							new FeatureNode(30,
									Double.parseDouble(item[wind_speed_index])) // 30th
					};

					/* train a row for transmission system */
					Feature[] transSys_ROW = new FeatureNode[] {
							new FeatureNode(1,
									Double.parseDouble(item[rotor_speed_index])),
							new FeatureNode(
									2,
									Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
							new FeatureNode(
									3,
									Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
							new FeatureNode(
									4,
									Double.parseDouble(item[main_bearing_gearbox_side_temperature_index])) };

					/* train a row for pitch system */
					Feature[] pitchSys_ROW = new FeatureNode[] {
							new FeatureNode(1,
									Double.parseDouble(item[rotor_speed_index])),
							new FeatureNode(
									2,
									Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
							new FeatureNode(
									3,
									Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
							new FeatureNode(
									4,
									Double.parseDouble(item[pitch_position_1_index])),
							new FeatureNode(
									5,
									Double.parseDouble(item[pitch_position_2_index])),
							new FeatureNode(
									6,
									Double.parseDouble(item[pitch_position_3_index])),
							new FeatureNode(
									7,
									Double.parseDouble(item[pitch_drive_current_1_index])),
							new FeatureNode(
									8,
									Double.parseDouble(item[pitch_drive_current_2_index])),
							new FeatureNode(
									9,
									Double.parseDouble(item[pitch_drive_current_3_index])),
							new FeatureNode(
									10,
									Double.parseDouble(item[pitch_ssb_motor_current_1_index])),
							new FeatureNode(
									11,
									Double.parseDouble(item[pitch_ssb_motor_current_2_index])),
							new FeatureNode(
									12,
									Double.parseDouble(item[pitch_ssb_motor_current_3_index])),
							new FeatureNode(
									13,
									Double.parseDouble(item[pitch_ssb_motor_temperature_1_index])),
							new FeatureNode(
									14,
									Double.parseDouble(item[pitch_ssb_motor_temperature_2_index])),
							new FeatureNode(
									15,
									Double.parseDouble(item[pitch_ssb_motor_temperature_3_index])) };

					/*
					 * train a row for electric system * /
					 */
					Feature[] electricSys_ROW = new FeatureNode[] {
							new FeatureNode(
									1,
									Double.parseDouble(item[generator_bearing_temperature_a_index])),
							new FeatureNode(
									2,
									Double.parseDouble(item[generator_bearing_temperature_b_index])),
							new FeatureNode(3,
									Double.parseDouble(item[grid_UL1_index])),
							new FeatureNode(4,
									Double.parseDouble(item[grid_UL2_index])),
							new FeatureNode(5,
									Double.parseDouble(item[grid_UL3_index])),
							new FeatureNode(6,
									Double.parseDouble(item[grid_I1_index])),
							new FeatureNode(7,
									Double.parseDouble(item[grid_I2_index])),
							new FeatureNode(8,
									Double.parseDouble(item[grid_I3_index])) };

					entireWT_X[dataRowNum] = entireWT_ROW;
					transmissionSys_X[dataRowNum] = transSys_ROW;
					pitchSys_X[dataRowNum] = pitchSys_ROW;
					electricSys_X[dataRowNum] = electricSys_ROW;

					entireWT_Y[dataRowNum] = Double
							.parseDouble(item[grid_power_index]);
					transmissionSys_Y[dataRowNum] = entireWT_Y[dataRowNum];
					pitchSys_Y[dataRowNum] = entireWT_Y[dataRowNum];
					electricSys_Y[dataRowNum] = entireWT_Y[dataRowNum];

					for (int i = 0; i < readDataInterval; i++) {
						if ((line = reader.readLine()) != null) {
							continue;
						} else {
							break;
						}

					}

					if (line == null) {
						// reach the end of this file
						break;
					}

				} // loop reading a single file

			}// end of for loop to read different files

			/* set x and y for models */
			entireWT.x = entireWT_X;
			entireWT.y = entireWT_Y;

			transmissionSys.x = transmissionSys_X;
			transmissionSys.y = transmissionSys_Y;

			pitchSys.x = pitchSys_X;
			pitchSys.y = pitchSys_Y;

			electricSys.x = electricSys_X;
			electricSys.y = electricSys_Y;

			/*
			 * set regression method and other params C:Ô¼ÊøviolationµÄ´ú¼Û²ÎÊý
			 * eps: µü´úÍ£Ö¹Ìõ¼þµÄÈÝÈÌ¶Ètolerance
			 */
			SolverType solver = SolverType.L2R_L2LOSS_SVR;
			double C = 1.0;
			double eps = 0.01;
			Parameter parameter = new Parameter(solver, C, eps);

			/* start trainning and save model */

			File modelDir = new File(MODEL_DIR);
			if (!modelDir.exists()) {
				modelDir.mkdirs();
			}
			Model entireModel = Linear.train(entireWT, parameter);
			File entireModelFile = new File(modelDir + File.separator
					+ "entire_model");

			Model transmissionSysModel = Linear.train(transmissionSys,
					parameter);
			File transModelFile = new File(modelDir + File.separator
					+ "transmission_model");

			Model pitchSysModel = Linear.train(pitchSys, parameter);
			File pitchSysFile = new File(modelDir + File.separator
					+ "pitch_model");

			Model electricSysModel = Linear.train(electricSys, parameter);
			File electricModelFile = new File(modelDir + File.separator
					+ "electric_model");
			try {
				entireModel.save(entireModelFile);
				transmissionSysModel.save(transModelFile);
				pitchSysModel.save(pitchSysFile);
				electricSysModel.save(electricModelFile);
				System.out.println("save model");
				System.out.println("train complete");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	/*
	 * 		added 2015.10.24 diagnose method
	 * 		boolean[] result has 4 elements: whole wind turbine result, transmission result, pitch result, electrical result
	 * 		factArray : 4 * DRAWING_IMAGE_POINTS_NUMBER(500)
	 * 		predictArray: 4 * DRAWING_IMAGE_POINTS_NUMBER(500)
	 */
	public void faultDiagnose(String date, String machineName, String dir,
			boolean[] fault, double[][] factArray, double[][] predictArray) {
		
		boolean hasFault = false;
		Model entireWT = null;
		int inputRowCount = 0;
		int calculateDataCount = 0;
		String line = null;
		String testFile = dir+File.separator+machineName+File.separator+date+".csv";
		
		File entireWTFile = new File(MODEL_DIR + File.separator
				+ "entire_model");

		/* load model */
		try {
			entireWT = Model.load(entireWTFile);
			System.out.println("load entireWTFile:" + testFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("io exception when load model file");
			return;
		}
		
		/* two tests of 2 10 minutes scada data */
		BufferedReader reader = null;
		double meanDeviation = 0;
		double deviationSum = 0;
		ArrayList<Double> wholePredict = new ArrayList<>();
		ArrayList<Double> wholeActual = new ArrayList<>();
		
		/*
		 * get line count of data
		 */
		try {
			reader = new BufferedReader(new FileReader(testFile));
			// pass first line which is the title
			reader.readLine();

			while ((line = reader.readLine()) != null) {
				inputRowCount++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {
				}
			}
		}
		
		System.out.println("inputRowCount:"+inputRowCount);
		
		try {
			reader = new BufferedReader(new FileReader(testFile));
			reader.readLine();
			for (int lineCount = 0; lineCount < inputRowCount; lineCount+=DATA_INTERVAL) {
				calculateDataCount ++ ;
				line = reader.readLine();
				if (line == null) {
					System.out.println("no data left");
					return;
				}
				String[] item = line.split(",");
				if(item.length<DATA_DIMENSION_NUM){
					continue;
				}
				Feature[] entireRow = new FeatureNode[] {
						new FeatureNode(
								1,
								Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
						new FeatureNode(
								2,
								Double.parseDouble(item[generator_bearing_temperature_a_index])),
						new FeatureNode(
								3,
								Double.parseDouble(item[generator_bearing_temperature_b_index])),
						new FeatureNode(4,
								Double.parseDouble(item[grid_I1_index])),
						new FeatureNode(5,
								Double.parseDouble(item[grid_I2_index])),
						new FeatureNode(6,
								Double.parseDouble(item[grid_I3_index])),
						new FeatureNode(7,
								Double.parseDouble(item[grid_UL1_index])),
						new FeatureNode(8,
								Double.parseDouble(item[grid_UL2_index])),
						new FeatureNode(9,
								Double.parseDouble(item[grid_UL3_index])),
						new FeatureNode(
								10,
								Double.parseDouble(item[main_bearing_gearbox_side_temperature_index])), // 10th
						new FeatureNode(
								11,
								Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
						new FeatureNode(
								12,
								Double.parseDouble(item[nacelle_temperature_index])),
						new FeatureNode(
								13,
								Double.parseDouble(item[nacelle_vibration_effective_value_index])),
						new FeatureNode(
								14,
								Double.parseDouble(item[nacelle_vibration_sensor_momentary_offset_max_index])),
						new FeatureNode(
								15,
								Double.parseDouble(item[nacelle_vibration_sensor_x_index])),
						new FeatureNode(
								16,
								Double.parseDouble(item[nacelle_vibration_sensor_y_index])),
						new FeatureNode(
								17,
								Double.parseDouble(item[pitch_drive_current_1_index])),
						new FeatureNode(
								18,
								Double.parseDouble(item[pitch_drive_current_2_index])),
						new FeatureNode(
								19,
								Double.parseDouble(item[pitch_drive_current_3_index])),
						new FeatureNode(
								20,
								Double.parseDouble(item[pitch_position_1_index])), // 20th
						new FeatureNode(
								21,
								Double.parseDouble(item[pitch_position_2_index])),
						new FeatureNode(
								22,
								Double.parseDouble(item[pitch_position_3_index])),
						new FeatureNode(
								23,
								Double.parseDouble(item[pitch_ssb_motor_current_1_index])),
						new FeatureNode(
								24,
								Double.parseDouble(item[pitch_ssb_motor_current_2_index])),
						new FeatureNode(
								25,
								Double.parseDouble(item[pitch_ssb_motor_current_3_index])),
						new FeatureNode(
								26,
								Double.parseDouble(item[pitch_ssb_motor_temperature_1_index])),
						new FeatureNode(
								27,
								Double.parseDouble(item[pitch_ssb_motor_temperature_2_index])),
						new FeatureNode(
								28,
								Double.parseDouble(item[pitch_ssb_motor_temperature_3_index])),
						new FeatureNode(29,
								Double.parseDouble(item[rotor_speed_index])),
						new FeatureNode(30,
								Double.parseDouble(item[wind_speed_index])) // 30th
				};
				double predict = Linear.predict(entireWT, entireRow);
				double fact = Double.parseDouble(item[grid_power_index]);

				wholePredict.add(predict);
				wholeActual.add(fact);

//				System.out.println("predict:"+predict+",fact:"+fact);
				if(fact<EPSILON){
					continue;
				}
				double deviation = Math.abs(predict - fact) / fact;
				deviationSum += deviation;
				
				// System.out
				// .println("line num " + lineCount + ", predict:"
				// + predict + ",fact:" + fact + ",deviation:"
				// + deviation);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			if(reader!=null){
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
		// parts

//		System.out.println("deviation sum: "+deviationSum+",calculateDataCount:"+calculateDataCount);
		meanDeviation = deviationSum / calculateDataCount;
		System.out.println("meanDeviation:" + meanDeviation);
//		ChartTool.drawChart(wholePredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER), 
//				wholeActual.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WHOLE_WT_CHART + " "
//				+ testFile);

		for(int i=0;i < wholePredict.size();i++){
			predictArray[0][i] = wholePredict.get(i);
			factArray[0][i] = wholeActual.get(i);
		}
		if (meanDeviation > WARNING_MEAN_DEVIATION) {
			System.out
					.println("Fault detected in the last ten minutes SCADA data.");
			hasFault = true;
			fault[0] = true;
		} else {
			System.out
					.println("Judging from the ten minutes SCADA data,the wind turbine performs well.");
			hasFault = false;
			fault[0] = false;
		}

		// /* if normal , return */
		// if( !hasFault ) return ;

		/* judge which part may has fault */
		Model transmissionSysModel = null;
		Model pitchSysModel = null;
		Model electricSysModel = null;
		File transmissionFile = new File(MODEL_DIR+File.separator+"transmission_model");
		File pitchFile = new File(MODEL_DIR+File.separator+"pitch_model");
		File electricFile = new File(MODEL_DIR+File.separator+"electric_model");

		ArrayList<Double> transPredict = new ArrayList<>();
		ArrayList<Double> transFact = new ArrayList<>();
		ArrayList<Double> pitchPredict = new ArrayList<>();
		ArrayList<Double> pitchFact = new ArrayList<>();
		ArrayList<Double> electricalPredict = new ArrayList<>();
		ArrayList<Double> electricalFact = new ArrayList<>();

		/* load model */
		try {
			transmissionSysModel = Model.load(transmissionFile);
			System.out.println("load transmissionSysModel");
			pitchSysModel = Model.load(pitchFile);
			System.out.println("load pitchSysModel");
			electricSysModel = Model.load(electricFile);
			System.out.println("load electricSysModel");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		BufferedReader reader2 = null;
		double transmissionDeviationSum = 0;
		double pitchDeviationSum = 0;
		double electricDeviationSum = 0;
		try {
			reader2 = new BufferedReader(new FileReader(testFile));
			// reader2 = new BufferedReader(new FileReader("C_1.csv"));
			reader2.readLine();

			for (int lineCount = 0; lineCount < inputRowCount; lineCount+=(DATA_INTERVAL-1)) {
				line = reader2.readLine();
				if (line == null) {
					System.out.println("no data left");
					return;
				}
				String[] item = line.split(",");
				Feature[] transRow = new FeatureNode[TRANS_SYS_DIMENSION];
				Feature[] pitchRow = new FeatureNode[PITCH_SYS_DIMENSION];
				Feature[] electricRow = new FeatureNode[ELECTRIC_SYS_DIMENSION];

				transRow = new FeatureNode[] {
						new FeatureNode(1,
								Double.parseDouble(item[rotor_speed_index])),
						new FeatureNode(
								2,
								Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
						new FeatureNode(
								3,
								Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
						new FeatureNode(
								4,
								Double.parseDouble(item[main_bearing_gearbox_side_temperature_index])) };

				pitchRow = new FeatureNode[] {
						new FeatureNode(1,
								Double.parseDouble(item[rotor_speed_index])),
						new FeatureNode(
								2,
								Double.parseDouble(item[gearbox_oil_temperature_oil_inlet_index])),
						new FeatureNode(
								3,
								Double.parseDouble(item[main_bearing_rotor_side_temperature_index])),
						new FeatureNode(
								4,
								Double.parseDouble(item[pitch_position_1_index])),
						new FeatureNode(
								5,
								Double.parseDouble(item[pitch_position_2_index])),
						new FeatureNode(
								6,
								Double.parseDouble(item[pitch_position_3_index])),
						new FeatureNode(
								7,
								Double.parseDouble(item[pitch_drive_current_1_index])),
						new FeatureNode(
								8,
								Double.parseDouble(item[pitch_drive_current_2_index])),
						new FeatureNode(
								9,
								Double.parseDouble(item[pitch_drive_current_3_index])),
						new FeatureNode(
								10,
								Double.parseDouble(item[pitch_ssb_motor_current_1_index])),
						new FeatureNode(
								11,
								Double.parseDouble(item[pitch_ssb_motor_current_2_index])),
						new FeatureNode(
								12,
								Double.parseDouble(item[pitch_ssb_motor_current_3_index])),
						new FeatureNode(
								13,
								Double.parseDouble(item[pitch_ssb_motor_temperature_1_index])),
						new FeatureNode(
								14,
								Double.parseDouble(item[pitch_ssb_motor_temperature_2_index])),
						new FeatureNode(
								15,
								Double.parseDouble(item[pitch_ssb_motor_temperature_3_index])) };

				electricRow = new FeatureNode[] {
						new FeatureNode(
								1,
								Double.parseDouble(item[generator_bearing_temperature_a_index])),
						new FeatureNode(
								2,
								Double.parseDouble(item[generator_bearing_temperature_b_index])),
						new FeatureNode(3,
								Double.parseDouble(item[grid_UL1_index])),
						new FeatureNode(4,
								Double.parseDouble(item[grid_UL2_index])),
						new FeatureNode(5,
								Double.parseDouble(item[grid_UL3_index])),
						new FeatureNode(6,
								Double.parseDouble(item[grid_I1_index])),
						new FeatureNode(7,
								Double.parseDouble(item[grid_I2_index])),
						new FeatureNode(8,
								Double.parseDouble(item[grid_I3_index])) };

				double predictTransRow = Linear.predict(transmissionSysModel,
						transRow);
				double predictPitchRow = Linear
						.predict(pitchSysModel, pitchRow);
				double predictElectricRow = Linear.predict(electricSysModel,
						electricRow);

				double gridPower = Double.parseDouble(item[grid_power_index]);
				if(gridPower<EPSILON){
					continue;
				}
				transPredict.add(predictTransRow);
				pitchPredict.add(predictPitchRow);
				electricalPredict.add(predictElectricRow);
				transFact.add(gridPower);
				pitchFact.add(gridPower);
				electricalFact.add(gridPower);

				double deviationTransRow = Math
						.abs(gridPower - predictTransRow) / gridPower;
				double deviationPitchRow = Math
						.abs(gridPower - predictPitchRow) / gridPower;
				double deviationElectricRow = Math.abs(gridPower
						- predictElectricRow)
						/ gridPower;

				transmissionDeviationSum += deviationTransRow;
				pitchDeviationSum += deviationPitchRow;
				electricDeviationSum += deviationElectricRow;

				// System.out.println("lineCount:"+lineCount+", deviationTransRow:"+deviationTransRow
				// +",deviationPitchRow:"+deviationPitchRow+",deviationElectricRow:"+deviationElectricRow);

			}

			double meanTransDeviation = transmissionDeviationSum
					/ calculateDataCount;
			double meanPitchDeviation = pitchDeviationSum / calculateDataCount;
			double meanElectricDeviation = electricDeviationSum / calculateDataCount;

			System.out.println("test file:" + testFile);
			System.out.println("meanTransDeviation:" + meanTransDeviation
					+ "\nmeanPitchDeviation:" + meanPitchDeviation
					+ "\nmeanElectricDeviation:" + meanElectricDeviation);
			
			for(int i=0;i < transPredict.size();i++){
				predictArray[1][i] = transPredict.get(i);
				predictArray[2][i] = pitchPredict.get(i);
				predictArray[3][i] = electricalPredict.get(i);
				
				factArray[1][i] = transFact.get(i);
				factArray[2][i] = pitchFact.get(i);
				factArray[3][i] = electricalFact.get(i);
			}

//			ChartTool.drawChart(transPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
//					transFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WT_TRANS_SYS_CHART);
//			ChartTool.sleep();
//			ChartTool.drawChart(pitchPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER), 
//					pitchFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WT_PITCH_SYS_CHART);
//			ChartTool.sleep();
//			ChartTool.drawChart(electricalPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
//					electricalFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
//					WT_ELECTRIC_SYS_CHART);

			if (meanTransDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Transmission System may have fault.");
				fault[1] = true;
			} else {
				System.out.println("Transmission System is ok");
				fault[1] = false;
			}

			if (meanPitchDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Pitch System may have fault.");
				fault[2] = true;
			} else {
				System.out.println("Pitch system is ok");
				fault[2] = false;
			}

			if (meanElectricDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Electrical System may have fault");
				fault[3] = true;
			} else {
				System.out.println("Electrical System is ok");
				fault[3] = false;
			}
			System.out.println();
			
			

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// String beginDate = "20150829.csv";
		// String endDate = "20150829.csv" + "" + "";
		// String dir = "C:\\Users\\andy\\Desktop";
		// String machineName = "tmp";
		// ArrayList<File> ans = findSuitableFiles(dir, machineName, beginDate,
		// endDate);
		// for (File f : ans) {
		// System.out.println("file name:" + f.toString());
		// diagnose(f.toString(), "c");
		// }

		Scanner scanner = new Scanner(System.in);
		String str = scanner.nextLine();
		ArrayList<String> ans = parseQueryString(str);
		for (int i = 0; i < ans.size(); i++) {
			System.out.println(ans.get(i));
		}
	}

	public static ArrayList<String> parseQueryString(String str) {
		/*
		 * return a ArrayList<String> ans[0] BeginDate ans[1] EndDate ans[2]
		 * Machine ans[3] DataPath
		 */
		if (str == null || str.length() == 0) {
			return null;
		}
		final int QUERY_ITEM_NUM = 4;
		ArrayList<String> ans = new ArrayList<>();
		String[] items = str.split(";");
		for (int i = 0; i < items.length; i++) {
			System.out.println(items[i]);
		}
		for (int i = 0; i < QUERY_ITEM_NUM; i++) {
			ans.add(items[i].split(":")[1]);
		}

		return ans;
	}

	/* used to find files according to begin date and end date */
	public static ArrayList<File> findSuitableFiles(String dir,
			String machineName, String beginDate, String endDate) {
		ArrayList<File> res = new ArrayList<>();
		if (beginDate.compareTo(endDate) > 0) {
			// begin date > end date
			System.out
					.println("err: begin date cannot be greater than end date");
			return res;
		}

		File targetDir = new File(dir, machineName);
		if (!targetDir.exists()) {
			System.out.println("err : target dir does not exist");
			return res;
		}

		recursiveListFiles(targetDir, res, beginDate, endDate);

		return res;
	}

	/* iterate the files under certain directory */
	public static void recursiveListFiles(File root, List<File> files,
			String beginDate, String endDate) {
		File[] lists = root.listFiles();
		for (File f : lists) {
			if (f.isFile()) {
				String[] arr = f.toString().split("\\\\");
				String fName = arr[arr.length - 1];
				if (fName.compareTo(beginDate) >= 0
						&& fName.compareTo(endDate) <= 0) {
					files.add(f);
				}

			} else {
				recursiveListFiles(f, files, beginDate, endDate);
			}
		}

	}

	/*
	 * use the model to calculate the deviation, and judge whether the wt is
	 * broken
//	 */
//	public static void diagnose(String testFile, String machinename) {
//		// String testFile = TEST_FILE_OK;
//		// String testFile = TEST_FILE_NOT_OK;
//
//		boolean hasFault = false;
//		Model entireWT = null;
//		File entireWTFile = new File("entire_model");
//		int lastIndexOfDot = testFile.lastIndexOf('.');
//		File resultFile = new File(testFile.substring(0, lastIndexOfDot)
//				+ ".result");
//		System.out.println("result file:" + resultFile.toString());
//		if (!resultFile.exists()) {
//			try {
//				resultFile.createNewFile();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//
//		/* load model */
//		try {
//			entireWT = Model.load(entireWTFile);
//			System.out.println("load entireWTFile");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			System.out.println("io exception when load model file");
//			return;
//		}
//
//		/* two tests of 2 10 minutes scada data */
//		BufferedReader reader = null;
//		BufferedWriter writer = null;
//		double meanDeviation = 0;
//		double deviationSum = 0;
//		ArrayList<Double> wholePredict = new ArrayList<>();
//		ArrayList<Double> wholeActual = new ArrayList<>();
//
//		try {
//			reader = new BufferedReader(new FileReader(testFile));
//			writer = new BufferedWriter(new FileWriter(resultFile));
//			// reader = new BufferedReader(new FileReader(testFile));
//			reader.readLine();
//			// int lineCount = 0;
//			for (int lineCount = 0; lineCount < TEM_MIN_RANGE; lineCount++) {
//				String line = reader.readLine();
//				if (line == null) {
//					System.out.println("no data left");
//					return;
//				}
//				String[] item = line.split(",");
//				Feature[] entireRow = new FeatureNode[ENTIRE_WT_DIMENSION];
//				for (int j = 0; j < ENTIRE_WT_DIMENSION; j++) {
//					if (j <= 15) {
//						entireRow[j] = new FeatureNode(j + 1,
//								Double.parseDouble(item[j + 1]));
//					} else {
//						entireRow[j] = new FeatureNode(j + 1,
//								Double.parseDouble(item[j + 2]));
//					}
//				}
//				double predict = Linear.predict(entireWT, entireRow);
//				double fact = Double.parseDouble(item[32]);
//
//				wholePredict.add(predict);
//				wholeActual.add(fact);
//
//				double deviation = Math.abs(predict - fact) / fact;
//				deviationSum += deviation;
//				// System.out
//				// .println("line num " + lineCount + ", predict:"
//				// + predict + ",fact:" + fact + ",deviation:"
//				// + deviation);
//			}
//
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} finally {
//			if (reader != null) {
//				try {
//					reader.close();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//
//		}
//
//		meanDeviation = deviationSum / TEM_MIN_RANGE;
//		System.out.println("meanDeviation:" + meanDeviation);
//		ChartTool.drawChart(wholePredict, wholeActual, WHOLE_WT_CHART + " "
//				+ testFile);
//
//		StringBuilder wholePredictBuilder = new StringBuilder();
//		StringBuilder wholeActualBuilder = new StringBuilder();
//
//		for (int i = 0; i < wholePredict.size(); i++) {
//			wholePredictBuilder.append(wholePredict.get(i) + ",");
//			wholeActualBuilder.append(wholeActual.get(i) + ",");
//		}
//		// remove last ','
//		wholePredictBuilder.deleteCharAt(wholePredictBuilder.length() - 1);
//		wholeActualBuilder.deleteCharAt(wholeActualBuilder.length() - 1);
//
//		try {
//			writer.write("wholePredict:" + wholePredictBuilder.toString()
//					+ "\n");
//			writer.write("wholeActual:" + wholeActualBuilder.toString() + "\n");
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//
//		if (meanDeviation > WARNING_MEAN_DEVIATION) {
//			System.out
//					.println("Fault detected in the last ten minutes SCADA data.");
//			hasFault = true;
//			try {
//				writer.write("whole has fault\n");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		} else {
//			System.out
//					.println("Judging from the ten minutes SCADA data,the wind turbine performs well.");
//			hasFault = false;
//			try {
//				writer.write("whole is ok\n");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//
//		if (!hasFault) {
//			try {
//				writer.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			return;
//		}
//
//		// /* if normal , return */
//		// if( !hasFault ) return ;
//
//		/* judge which part may has fault */
//		Model transmissionSysModel = null;
//		Model pitchSysModel = null;
//		Model electricSysModel = null;
//		File transmissionFile = new File("transmission_model");
//		File pitchFile = new File("pitch_model");
//		File electricFile = new File("electric_model");
//
//		ArrayList<Double> transPredict = new ArrayList<>();
//		ArrayList<Double> transFact = new ArrayList<>();
//		ArrayList<Double> pitchPredict = new ArrayList<>();
//		ArrayList<Double> pitchFact = new ArrayList<>();
//		ArrayList<Double> electricalPredict = new ArrayList<>();
//		ArrayList<Double> electricalFact = new ArrayList<>();
//
//		/* load model */
//		try {
//			transmissionSysModel = Model.load(transmissionFile);
//			System.out.println("load transmissionSysModel");
//			pitchSysModel = Model.load(pitchFile);
//			System.out.println("load pitchSysModel");
//			electricSysModel = Model.load(electricFile);
//			System.out.println("load electricSysModel");
//
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		BufferedReader reader2 = null;
//		double transmissionDeviationSum = 0;
//		double pitchDeviationSum = 0;
//		double electricDeviationSum = 0;
//		try {
//			reader2 = new BufferedReader(new FileReader(testFile));
//			// reader2 = new BufferedReader(new FileReader("C_1.csv"));
//			reader2.readLine();
//
//			for (int lineCount = 0; lineCount < TEM_MIN_RANGE; lineCount++) {
//				String line = reader2.readLine();
//				if (line == null) {
//					System.out.println("no data left");
//					return;
//				}
//				String[] item = line.split(",");
//				Feature[] transRow = new FeatureNode[TRANS_SYS_DIMENSION];
//				Feature[] pitchRow = new FeatureNode[PITCH_SYS_DIMENSION];
//				Feature[] electricRow = new FeatureNode[ELECTRIC_SYS_DIMENSION];
//
//				transRow = new FeatureNode[] {
//						new FeatureNode(1, Double.parseDouble(item[6])),
//						new FeatureNode(2, Double.parseDouble(item[7])),
//						new FeatureNode(3, Double.parseDouble(item[18])),
//						new FeatureNode(4, Double.parseDouble(item[19])) };
//
//				pitchRow = new FeatureNode[] {
//						new FeatureNode(1, Double.parseDouble(item[6])),
//						new FeatureNode(2, Double.parseDouble(item[7])),
//						new FeatureNode(3, Double.parseDouble(item[18])),
//						new FeatureNode(4, Double.parseDouble(item[20])),
//						new FeatureNode(5, Double.parseDouble(item[21])),
//						new FeatureNode(6, Double.parseDouble(item[22])),
//						new FeatureNode(7, Double.parseDouble(item[23])),
//						new FeatureNode(8, Double.parseDouble(item[24])),
//						new FeatureNode(9, Double.parseDouble(item[25])),
//						new FeatureNode(10, Double.parseDouble(item[26])),
//						new FeatureNode(11, Double.parseDouble(item[27])),
//						new FeatureNode(12, Double.parseDouble(item[28])),
//						new FeatureNode(13, Double.parseDouble(item[29])),
//						new FeatureNode(14, Double.parseDouble(item[30])),
//						new FeatureNode(15, Double.parseDouble(item[31])) };
//
//				electricRow = new FeatureNode[] {
//						new FeatureNode(1, Double.parseDouble(item[8])),
//						new FeatureNode(2, Double.parseDouble(item[9])),
//						new FeatureNode(3, Double.parseDouble(item[11])),
//						new FeatureNode(4, Double.parseDouble(item[12])),
//						new FeatureNode(5, Double.parseDouble(item[13])),
//						new FeatureNode(6, Double.parseDouble(item[14])),
//						new FeatureNode(7, Double.parseDouble(item[15])),
//						new FeatureNode(8, Double.parseDouble(item[16])),
//
//				};
//
//				double predictTransRow = Linear.predict(transmissionSysModel,
//						transRow);
//				double predictPitchRow = Linear
//						.predict(pitchSysModel, pitchRow);
//				double predictElectricRow = Linear.predict(electricSysModel,
//						electricRow);
//
//				transPredict.add(predictElectricRow);
//				pitchPredict.add(predictPitchRow);
//				electricalPredict.add(predictElectricRow);
//				transFact.add(1.0);
//				pitchFact.add(1.0);
//				electricalFact.add(1.0);
//
//				double deviationTransRow = Math.abs(1 - predictTransRow)
//						/ predictTransRow;
//				double deviationPitchRow = Math.abs(1 - predictPitchRow)
//						/ predictPitchRow;
//				double deviationElectricRow = Math.abs(1 - predictElectricRow)
//						/ predictElectricRow;
//
//				transmissionDeviationSum += deviationTransRow;
//				pitchDeviationSum += deviationPitchRow;
//				electricDeviationSum += deviationElectricRow;
//
//				// System.out.println("lineCount:" + lineCount
//				// + ", deviationTransRow:" + deviationTransRow
//				// + ",deviationPitchRow:" + deviationPitchRow
//				// + ",deviationElectricRow:" + deviationElectricRow);
//
//			}
//
//			double meanTransDeviation = transmissionDeviationSum
//					/ TEM_MIN_RANGE;
//			double meanPitchDeviation = pitchDeviationSum / TEM_MIN_RANGE;
//			double meanElectricDeviation = electricDeviationSum / TEM_MIN_RANGE;
//
//			System.out.println("test file:" + testFile);
//			System.out.println("meanTransDeviation:" + meanTransDeviation
//					+ "\nmeanPitchDeviation:" + meanPitchDeviation
//					+ "\nmeanElectricDeviation:" + meanElectricDeviation);
//
//			ChartTool.drawChart(transPredict, transFact, WT_TRANS_SYS_CHART);
//			ChartTool.sleep();
//			ChartTool.drawChart(pitchPredict, pitchFact, WT_PITCH_SYS_CHART);
//			ChartTool.sleep();
//			ChartTool.drawChart(electricalPredict, electricalFact,
//					WT_ELECTRIC_SYS_CHART);
//
//			StringBuilder transPredictBuilder = new StringBuilder();
//			StringBuilder transFactBuilder = new StringBuilder();
//			StringBuilder pitchPredictBuilder = new StringBuilder();
//			StringBuilder pitchFactBuilder = new StringBuilder();
//			StringBuilder electricalPredictBuilder = new StringBuilder();
//			StringBuilder electricalFactBuilder = new StringBuilder();
//
//			for (int i = 0; i < transPredict.size(); i++) {
//				transPredictBuilder.append(transPredict.get(i) + ",");
//				transFactBuilder.append(transFact.get(i) + ",");
//				pitchPredictBuilder.append(pitchPredict.get(i) + ",");
//				pitchFactBuilder.append(pitchFact.get(i) + ",");
//				electricalPredictBuilder.append(electricalPredict.get(i) + ",");
//				electricalFactBuilder.append(electricalFact.get(i) + ",");
//			}
//
//			// remove last ','
//			transPredictBuilder.deleteCharAt(transPredictBuilder.length() - 1);
//			transFactBuilder.deleteCharAt(transFactBuilder.length() - 1);
//			pitchPredictBuilder.deleteCharAt(pitchPredictBuilder.length() - 1);
//			pitchFactBuilder.deleteCharAt(pitchFactBuilder.length() - 1);
//			electricalPredictBuilder.deleteCharAt(electricalPredictBuilder
//					.length() - 1);
//			electricalFactBuilder
//					.deleteCharAt(electricalFactBuilder.length() - 1);
//
//			writer.write("transPredict:" + transPredictBuilder.toString()
//					+ "\n");
//			writer.write("transFact:" + transFactBuilder.toString() + "\n");
//			writer.write("pitchPredict:" + pitchPredictBuilder.toString()
//					+ "\n");
//			writer.write("pitchFact:" + pitchFactBuilder.toString() + "\n");
//			writer.write("electricalPredict:"
//					+ electricalPredictBuilder.toString() + "\n");
//			writer.write("electricalFact:" + electricalFactBuilder.toString()
//					+ "\n");
//
//			if (meanTransDeviation > PARTIAL_FAULT_LIMIT) {
//				System.out.println("Transmission System may have fault.\n");
//				try {
//					writer.write("trans has fault");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			} else {
//				System.out.println("Transmission System is ok");
//				try {
//					writer.write("trans is ok\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//
//			if (meanPitchDeviation > PARTIAL_FAULT_LIMIT) {
//				System.out.println("Pitch System may have fault.");
//				try {
//					writer.write("pitch has fault\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			} else {
//				System.out.println("Pitch system is ok");
//				try {
//					writer.write("pitch is ok\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//
//			if (meanElectricDeviation > PARTIAL_FAULT_LIMIT) {
//				System.out.println("Electrical System may have fault");
//				try {
//					writer.write("electrical has fault\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			} else {
//				System.out.println("Electrical System is ok");
//				try {
//					writer.write("electrical is ok\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//			System.out.println();
//
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} finally {
//			if (writer != null) {
//				try {
//					writer.close();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//		}
//
//	}

}
