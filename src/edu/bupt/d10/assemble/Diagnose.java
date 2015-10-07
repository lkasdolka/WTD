package edu.bupt.d10.assemble;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import edu.bupt.d10.ChartTool;
import edu.bupt.d10.Test;

public class Diagnose {

	private static final int TEM_MIN_RANGE = 10 * 60;
	private static final int ENTIRE_WT_DIMENSION = 30;
	private static final double WARNING_MEAN_DEVIATION = 0.05;
	private static final int TRANS_SYS_DIMENSION = 4;
	private static final int PITCH_SYS_DIMENSION = 15;
	private static final int ELECTRIC_SYS_DIMENSION = 8;
	private static final double PARTIAL_FAULT_LIMIT = 0.1;
	private static final String TEST_FILE_OK = "B_3.csv";
	private static final String TEST_FILE_NOT_OK = "C_1.csv";

	private static final String WHOLE_WT_CHART = "Whole Wind Turbine";
	private static final String WT_TRANS_SYS_CHART = "Transmission System";
	private static final String WT_PITCH_SYS_CHART = "Pitch System";
	private static final String WT_ELECTRIC_SYS_CHART = "Electrical System";

	public static void main(String[] args) {
//		String beginDate = "20150829.csv";
//		String endDate = "20150829.csv" + "" + "";
//		String dir = "C:\\Users\\andy\\Desktop";
//		String machineName = "tmp";
//		ArrayList<File> ans = findSuitableFiles(dir, machineName, beginDate,
//				endDate);
//		for (File f : ans) {
//			System.out.println("file name:" + f.toString());
//			diagnose(f.toString(), "c");
//		}
		
		
		Scanner scanner = new Scanner(System.in);
		String str = scanner.nextLine();
		ArrayList<String> ans = parseQueryString(str);
		for(int i=0; i < ans.size() ; i++){
			System.out.println(ans.get(i));
		}
	}
	
	public static ArrayList<String> parseQueryString(String str){
		/*
		 * 		return a ArrayList<String> 
		 * 		ans[0] BeginDate
		 * 		ans[1] EndDate
		 * 		ans[2] Machine
		 * 		ans[3] DataPath
		 * 
		 * */
		if(str == null || str.length() == 0){
			return null;
		}
		final int QUERY_ITEM_NUM = 4;
		ArrayList<String> ans = new ArrayList<>();
		String[] items = str.split(";");
		for(int i=0;i<items.length;i++){
			System.out.println(items[i]);
		}
		for(int i = 0; i<QUERY_ITEM_NUM ; i++ ){
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

	/* use the model to calculate the deviation, and judge whether the wt is broken */
	public static void diagnose(String testFile, String machinename) {
		// String testFile = TEST_FILE_OK;
		// String testFile = TEST_FILE_NOT_OK;

		boolean hasFault = false;
		Model entireWT = null;
		File entireWTFile = new File("entire_model");
		int lastIndexOfDot = testFile.lastIndexOf('.');
		File resultFile = new File(testFile.substring(0,lastIndexOfDot)+ ".result");
		System.out.println("result file:"+resultFile.toString());
		if(!resultFile.exists()){
			try {
				resultFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/* load model */
		try {
			entireWT = Model.load(entireWTFile);
			System.out.println("load entireWTFile");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("io exception when load model file");
			return;
		}

		/* two tests of 2 10 minutes scada data */
		BufferedReader reader = null;
		BufferedWriter writer = null;
		double meanDeviation = 0;
		double deviationSum = 0;
		ArrayList<Double> wholePredict = new ArrayList<>();
		ArrayList<Double> wholeActual = new ArrayList<>();

		try {
			reader = new BufferedReader(new FileReader(testFile));
			writer = new BufferedWriter(new FileWriter(resultFile));
			// reader = new BufferedReader(new FileReader(testFile));
			reader.readLine();
			// int lineCount = 0;
			for (int lineCount = 0; lineCount < TEM_MIN_RANGE; lineCount++) {
				String line = reader.readLine();
				if (line == null) {
					System.out.println("no data left");
					return;
				}
				String[] item = line.split(",");
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
				double fact = Double.parseDouble(item[32]);

				wholePredict.add(predict);
				wholeActual.add(fact);

				double deviation = Math.abs(predict - fact) / fact;
				deviationSum += deviation;
//				System.out
//						.println("line num " + lineCount + ", predict:"
//								+ predict + ",fact:" + fact + ",deviation:"
//								+ deviation);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
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

		meanDeviation = deviationSum / TEM_MIN_RANGE;
		System.out.println("meanDeviation:" + meanDeviation);
		ChartTool.drawChart(wholePredict, wholeActual, WHOLE_WT_CHART + " "
				+ testFile);
		
		StringBuilder wholePredictBuilder = new StringBuilder();
		StringBuilder wholeActualBuilder = new StringBuilder();
		
		for(int i = 0; i < wholePredict.size() ; i ++ ){
			wholePredictBuilder.append(wholePredict.get(i)+",");
			wholeActualBuilder.append(wholeActual.get(i)+",");
		}
		//remove last ','
		wholePredictBuilder.deleteCharAt(wholePredictBuilder.length()-1);
		wholeActualBuilder.deleteCharAt(wholeActualBuilder.length()-1);
		
		try {
			writer.write("wholePredict:"+wholePredictBuilder.toString()+"\n");
			writer.write("wholeActual:"+wholeActualBuilder.toString()+"\n");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		

		if (meanDeviation > WARNING_MEAN_DEVIATION) {
			System.out
					.println("Fault detected in the last ten minutes SCADA data.");
			hasFault = true;
			try {
				writer.write("whole has fault\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out
					.println("Judging from the ten minutes SCADA data,the wind turbine performs well.");
			hasFault = false;
			try {
				writer.write("whole is ok\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(!hasFault){
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			return ;
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

			for (int lineCount = 0; lineCount < TEM_MIN_RANGE; lineCount++) {
				String line = reader2.readLine();
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

				transPredict.add(predictElectricRow);
				pitchPredict.add(predictPitchRow);
				electricalPredict.add(predictElectricRow);
				transFact.add(1.0);
				pitchFact.add(1.0);
				electricalFact.add(1.0);

				double deviationTransRow = Math.abs(1 - predictTransRow)
						/ predictTransRow;
				double deviationPitchRow = Math.abs(1 - predictPitchRow)
						/ predictPitchRow;
				double deviationElectricRow = Math.abs(1 - predictElectricRow)
						/ predictElectricRow;

				transmissionDeviationSum += deviationTransRow;
				pitchDeviationSum += deviationPitchRow;
				electricDeviationSum += deviationElectricRow;

//				System.out.println("lineCount:" + lineCount
//						+ ", deviationTransRow:" + deviationTransRow
//						+ ",deviationPitchRow:" + deviationPitchRow
//						+ ",deviationElectricRow:" + deviationElectricRow);

			}

			double meanTransDeviation = transmissionDeviationSum
					/ TEM_MIN_RANGE;
			double meanPitchDeviation = pitchDeviationSum / TEM_MIN_RANGE;
			double meanElectricDeviation = electricDeviationSum / TEM_MIN_RANGE;

			System.out.println("test file:" + testFile);
			System.out.println("meanTransDeviation:" + meanTransDeviation
					+ "\nmeanPitchDeviation:" + meanPitchDeviation
					+ "\nmeanElectricDeviation:" + meanElectricDeviation);

			ChartTool.drawChart(transPredict, transFact, WT_TRANS_SYS_CHART);
			ChartTool.sleep();
			ChartTool.drawChart(pitchPredict, pitchFact, WT_PITCH_SYS_CHART);
			ChartTool.sleep();
			ChartTool.drawChart(electricalPredict, electricalFact,
					WT_ELECTRIC_SYS_CHART);
			
			StringBuilder transPredictBuilder = new StringBuilder();
			StringBuilder transFactBuilder = new StringBuilder();
			StringBuilder pitchPredictBuilder = new StringBuilder();
			StringBuilder pitchFactBuilder = new StringBuilder();
			StringBuilder electricalPredictBuilder = new StringBuilder();
			StringBuilder electricalFactBuilder = new StringBuilder();
			
			for(int i = 0; i < transPredict.size(); i++ ){
				transPredictBuilder.append(transPredict.get(i)+",");
				transFactBuilder.append(transFact.get(i)+",");
				pitchPredictBuilder.append(pitchPredict.get(i)+",");
				pitchFactBuilder.append(pitchFact.get(i)+",");
				electricalPredictBuilder.append(electricalPredict.get(i)+",");
				electricalFactBuilder.append(electricalFact.get(i)+",");
			}
			
			//remove last ','
			transPredictBuilder.deleteCharAt(transPredictBuilder.length()-1);
			transFactBuilder.deleteCharAt(transFactBuilder.length()-1);
			pitchPredictBuilder.deleteCharAt(pitchPredictBuilder.length()-1);
			pitchFactBuilder.deleteCharAt(pitchFactBuilder.length()-1);
			electricalPredictBuilder.deleteCharAt(electricalPredictBuilder.length()-1);
			electricalFactBuilder.deleteCharAt(electricalFactBuilder.length()-1);
			
			writer.write("transPredict:"+transPredictBuilder.toString()+"\n");
			writer.write("transFact:"+transFactBuilder.toString()+"\n");
			writer.write("pitchPredict:"+pitchPredictBuilder.toString()+"\n");
			writer.write("pitchFact:"+pitchFactBuilder.toString()+"\n");
			writer.write("electricalPredict:"+electricalPredictBuilder.toString()+"\n");
			writer.write("electricalFact:"+electricalFactBuilder.toString()+"\n");
			
			

			if (meanTransDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Transmission System may have fault.\n");
				try {
					writer.write("trans has fault");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println("Transmission System is ok");
				try {
					writer.write("trans is ok\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (meanPitchDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Pitch System may have fault.");
				try {
					writer.write("pitch has fault\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println("Pitch system is ok");
				try {
					writer.write("pitch is ok\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (meanElectricDeviation > PARTIAL_FAULT_LIMIT) {
				System.out.println("Electrical System may have fault");
				try {
					writer.write("electrical has fault\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println("Electrical System is ok");
				try {
					writer.write("electrical is ok\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			if(writer != null){
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

}
