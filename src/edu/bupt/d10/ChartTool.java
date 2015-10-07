package edu.bupt.d10;

import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.Data;

import com.xeiam.xchart.Chart;
import com.xeiam.xchart.ChartBuilder;
import com.xeiam.xchart.SwingWrapper;
import com.xeiam.xchart.StyleManager.ChartType;
import com.xeiam.xchart.StyleManager.LegendPosition;

public class ChartTool {
	public static void drawChart(List<Double> predict, List<Double> fact,String name){
		if(predict==null || fact == null | predict.size() != fact.size()){
			System.out.println("data not meet standard");
		}
		int length = predict.size();
			
		if(length == 0){
			System.out.println("data is null , return");
		}
		List<Double> xList = new ArrayList<Double>();
		for(int i = 0; i < length; i ++){
			xList.add((double)i);
		}
		
		System.out.println("begin draw");
		
		Chart chart = new ChartBuilder().chartType(ChartType.Line)
				.width(800).height(600).title(name).xAxisTitle("x")
				.yAxisTitle("y").build(); 
		
		chart.addSeries("predict", xList, predict);
		chart.addSeries("fact", xList, fact);
		
		chart.getStyleManager().setLegendPosition(LegendPosition.InsideNW);
		
		new SwingWrapper(chart).displayChart();
		
		System.out.println("draw complete");
	}
	public static void sleep(){
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
