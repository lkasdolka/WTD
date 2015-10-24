package edu.bupt.d10.original;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.util.ArrayList;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

public class Test {
	private static final int TEM_MIN_RANGE = 10 * 60;
	private static final int ENTIRE_WT_DIMENSION = 30;
	private static final double WARNING_MEAN_DEVIATION = 0.015;
	private static final int TRANS_SYS_DIMENSION = 4;
	private static final int PITCH_SYS_DIMENSION = 15;
	private static final int ELECTRIC_SYS_DIMENSION = 8;
	private static final double PARTIAL_FAULT_LIMIT = 0.5;
	private static final int DATA_DIMENSION_NUM = 33;
	private static final double EPSILON = 0.00001;
	private static final int DATA_INTERVAL = 10;
	private static final int DRAWING_IMAGE_POINTS_NUMBER = 500;
	private static final String TEST_FILE_OK = "c.csv";
	private static final String TEST_FILE_NOT_OK = "b.csv";

	private static final String WHOLE_WT_CHART = "Whole Wind Turbine";
	private static final String WT_TRANS_SYS_CHART = "Transmission System";
	private static final String WT_PITCH_SYS_CHART = "Pitch System";
	private static final String WT_ELECTRIC_SYS_CHART = "Electrical System";

	public static void test() {
//		 String testFile = TEST_FILE_OK;
		String testFile = TEST_FILE_NOT_OK;

		boolean hasFault = false;
		Model entireWT = null;
		File entireWTFile = new File("entire_model");
		int inputRowCount = 0;
		String line = null;
		int calculateDataCount = 0;

		/* load model */
		try {
			entireWT = Model.load(entireWTFile);
			System.out.println("load entireWTFile:"+testFile);
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
			// reader = new BufferedReader(new FileReader(testFile));
			reader.readLine();
			// int lineCount = 0;
			for (int lineCount = 0; lineCount < inputRowCount; lineCount+=(DATA_INTERVAL-1)) {
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
				Feature[] entireRow = new FeatureNode[ENTIRE_WT_DIMENSION];
				for (int j = 0; j < ENTIRE_WT_DIMENSION; j++) {
					if (j <= 15) {
						entireRow[j] = new FeatureNode(j + 1,
								Double.parseDouble(item[j + 1]));
					} else {
						entireRow[j] = new FeatureNode(j + 1,
								Double.parseDouble(item[j + 2]));
					}
				}
				double predict = Linear.predict(entireWT, entireRow);
				double fact = Double.parseDouble(item[17]);

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
		}
//		System.out.println("deviation sum: "+deviationSum+",calculateDataCount:"+calculateDataCount);
		meanDeviation = deviationSum / calculateDataCount;
		System.out.println("meanDeviation:" + meanDeviation);
		ChartTool.drawChart(wholePredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER), 
				wholeActual.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WHOLE_WT_CHART + " "
				+ testFile);

		if (meanDeviation > WARNING_MEAN_DEVIATION) {
			System.out
					.println("Fault detected in the last ten minutes SCADA data.");
			hasFault = true;
		} else {
			System.out
					.println("Judging from the ten minutes SCADA data,the wind turbine performs well.");
			hasFault = false;
		}

		// /* if normal , return */
		// if( !hasFault ) return ;

		/* judge which part may has fault */
		Model transmissionSysModel = null;
		Model pitchSysModel = null;
		Model electricSysModel = null;
		File transmissionFile = new File("transmission_model");
		File pitchFile = new File("pitch_model");
		File electricFile = new File("electric_model");

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
						new FeatureNode(1, Double.parseDouble(item[6])),
						new FeatureNode(2, Double.parseDouble(item[7])),
						new FeatureNode(3, Double.parseDouble(item[18])),
						new FeatureNode(4, Double.parseDouble(item[19])) };

				pitchRow = new FeatureNode[] {
						new FeatureNode(1, Double.parseDouble(item[6])),
						new FeatureNode(2, Double.parseDouble(item[7])),
						new FeatureNode(3, Double.parseDouble(item[18])),
						new FeatureNode(4, Double.parseDouble(item[20])),
						new FeatureNode(5, Double.parseDouble(item[21])),
						new FeatureNode(6, Double.parseDouble(item[22])),
						new FeatureNode(7, Double.parseDouble(item[23])),
						new FeatureNode(8, Double.parseDouble(item[24])),
						new FeatureNode(9, Double.parseDouble(item[25])),
						new FeatureNode(10, Double.parseDouble(item[26])),
						new FeatureNode(11, Double.parseDouble(item[27])),
						new FeatureNode(12, Double.parseDouble(item[28])),
						new FeatureNode(13, Double.parseDouble(item[29])),
						new FeatureNode(14, Double.parseDouble(item[30])),
						new FeatureNode(15, Double.parseDouble(item[31])) };

				electricRow = new FeatureNode[] {
						new FeatureNode(1, Double.parseDouble(item[8])),
						new FeatureNode(2, Double.parseDouble(item[9])),
						new FeatureNode(3, Double.parseDouble(item[11])),
						new FeatureNode(4, Double.parseDouble(item[12])),
						new FeatureNode(5, Double.parseDouble(item[13])),
						new FeatureNode(6, Double.parseDouble(item[14])),
						new FeatureNode(7, Double.parseDouble(item[15])),
						new FeatureNode(8, Double.parseDouble(item[16])),

				};

				double predictTransRow = Linear.predict(transmissionSysModel,
						transRow);
				double predictPitchRow = Linear
						.predict(pitchSysModel, pitchRow);
				double predictElectricRow = Linear.predict(electricSysModel,
						electricRow);

				double gridPower = Double.parseDouble(item[17]);
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

			ChartTool.drawChart(transPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
					transFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WT_TRANS_SYS_CHART);
			ChartTool.sleep();
			ChartTool.drawChart(pitchPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER), 
					pitchFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER), WT_PITCH_SYS_CHART);
			ChartTool.sleep();
			ChartTool.drawChart(electricalPredict.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
					electricalFact.subList(0, DRAWING_IMAGE_POINTS_NUMBER),
					WT_ELECTRIC_SYS_CHART);

			if (meanTransDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Transmission System may have fault.");
			} else {
				System.out.println("Transmission System is ok");
			}

			if (meanPitchDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Pitch System may have fault.");
			} else {
				System.out.println("Pitch system is ok");
			}

			if (meanElectricDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Electrical System may have fault");
			} else {
				System.out.println("Electrical System is ok");
			}
			System.out.println();
			
			

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		Test.test();
	}
}
