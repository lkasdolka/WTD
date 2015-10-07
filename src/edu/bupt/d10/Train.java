package edu.bupt.d10;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.DoubleBuffer;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

public class Train {
	private static final int DATA_INTERVAL = 30;
	private static final int ENTIRE_WT_DIMENSION = 30;
	private static final int TRANS_SYS_DIMENSION = 4;
	private static final int PITCH_SYS_DIMENSION = 15;
	private static final int ELECTRIC_SYS_DIMENSION = 8;
	private static final int LINES_TO_JUMP = 29;

	public static void trainModel() {

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

		BufferedReader reader = null;
		String line = null;
		int dataRowNum = 0;

		/*
		 * get line count of data
		 */
		try {
			reader = new BufferedReader(new FileReader("A_training.csv"));
			// pass first line which is the title
			reader.readLine();

			while ((line = reader.readLine()) != null) {
				dataRowNum++;
				String[] item = line.split(",");
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {
				}
			}
		}

		/*
		 * define model dimension
		 */
		int trainingSetNum = dataRowNum / DATA_INTERVAL;
		System.out.println("totalLineNum:" + dataRowNum + ",trainingSetNum:"
				+ trainingSetNum);
		
		/*
		 				problem.l  number of training examples：训练样本数
				        problem.n  number of features：特征维数
         				problem.x  feature nodes：特征数据
         				problem.y  target values：类别/输出值
		 
		 */

		
		
		/*
		 * l marks training set size n marks training data feature dimension x
		 * marks input y marks output
		 */
		entireWT.l = trainingSetNum;
		transmissionSys.l = trainingSetNum;
		pitchSys.l = trainingSetNum;
		electricSys.l = trainingSetNum;

		entireWT.n = 30;
		transmissionSys.n = 4;
		pitchSys.n = 15;
		electricSys.n = 8;

		
		/* X is input */
		Feature[][] entireWT_X = new FeatureNode[trainingSetNum][];
		Feature[][] transmissionSys_X = new FeatureNode[trainingSetNum][];
		Feature[][] pitchSys_X = new FeatureNode[trainingSetNum][];
		Feature[][] electricSys_X = new FeatureNode[trainingSetNum][];

		/* Y is output*/
		double[] entireWT_Y = new double[trainingSetNum];
		double[] transmissionSys_Y = new double[trainingSetNum];
		double[] pitchSys_Y = new double[trainingSetNum];
		double[] electricSys_Y = new double[trainingSetNum];

		/* prepare input data */
		try {
			reader = new BufferedReader(new FileReader("A_training.csv"));

			/* pass first line which is the title */
			reader.readLine();
			int count = -1;

			while ((line = reader.readLine()) != null) {
				count++;
				String[] item = line.split(",");
				System.out.println("item's length:" + item.length);
				/*		*/
				Feature[] entireWT_ROW = new FeatureNode[ENTIRE_WT_DIMENSION];

				/* train a row for entire wt */
				for (int i = 0; i < ENTIRE_WT_DIMENSION; i++) {
					if (i <= 15) {
						entireWT_ROW[i] = new FeatureNode(i + 1,
								Double.parseDouble(item[i + 1]));
					} else {
						/*skip grid power , which is output option*/
						entireWT_ROW[i] = new FeatureNode(i + 1,
								Double.parseDouble(item[i + 2]));
					}
				}

				/* train a row for transmission system */
				Feature[] transSys_ROW = new FeatureNode[] {
						new FeatureNode(1, Double.parseDouble(item[6])),
						new FeatureNode(2, Double.parseDouble(item[7])),
						new FeatureNode(3, Double.parseDouble(item[18])),
						new FeatureNode(4, Double.parseDouble(item[19])) };

				/* train a row for pitch system */
				Feature[] pitchSys_ROW = new FeatureNode[] {
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

				/*
				 * train a row for electric system * /
				 */
				Feature[] electricSys_ROW = new FeatureNode[] {
						new FeatureNode(1, Double.parseDouble(item[8])),
						new FeatureNode(2, Double.parseDouble(item[9])),
						new FeatureNode(3, Double.parseDouble(item[11])),
						new FeatureNode(4, Double.parseDouble(item[12])),
						new FeatureNode(5, Double.parseDouble(item[13])),
						new FeatureNode(6, Double.parseDouble(item[14])),
						new FeatureNode(7, Double.parseDouble(item[15])),
						new FeatureNode(8, Double.parseDouble(item[16])),

				};

				entireWT_X[count] = entireWT_ROW;
				transmissionSys_X[count] = transSys_ROW;
				pitchSys_X[count] = pitchSys_ROW;
				electricSys_X[count] = electricSys_ROW;

				entireWT_Y[count] = Double.parseDouble(item[17]);
				transmissionSys_Y[count] = 1;
				pitchSys_Y[count] = 1;
				electricSys_Y[count] = 1;

				/* jump 29 lines */
				for (int i = 0; i < LINES_TO_JUMP; i++) {
					if ((line = reader.readLine()) != null)
						continue;
				}

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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
		 * set regression method and other params C:约束violation的代价参数 eps:
		 * 迭代停止条件的容忍度tolerance
		 */
		SolverType solver = SolverType.L2R_L2LOSS_SVR;
		double C = 1.0;
		double eps = 0.01;
		Parameter parameter = new Parameter(solver, C, eps);

		/* start trainning and save model */

		Model entireModel = Linear.train(entireWT, parameter);
		File entireModelFile = new File("entire_model");

		Model transmissionSysModel = Linear.train(transmissionSys, parameter);
		File transModelFile = new File("transmission_model");

		Model pitchSysModel = Linear.train(pitchSys, parameter);
		File pitchSysFile = new File("pitch_model");

		Model electricSysModel = Linear.train(electricSys, parameter);
		File electricModelFile = new File("electric_model");
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

	}
	
	public static void main(String[] args) {
		Train.trainModel();
	}
}
