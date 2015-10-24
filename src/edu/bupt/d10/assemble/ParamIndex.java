package edu.bupt.d10.assemble;

public interface ParamIndex {
	
	/*
	 * 		column index starts from 1, 
	 * */
	int time_index = 0;
	int converter_power_index = 1; // no use
	int gearbox_oil_temperature_oil_inlet_index = 2;
	int generator_bearing_temperature_a_index = 3;
	int generator_bearing_temperature_b_index = 4;
	int grid_I1_index = 5;
	int grid_I2_index = 6;
	int grid_I3_index = 7;
	int grid_UL1_index = 8;
	int grid_UL2_index = 9;
	int grid_UL3_index = 10;
	int grid_power_index = 11; // output
	int main_bearing_gearbox_side_temperature_index = 12;
	int main_bearing_rotor_side_temperature_index = 13;
	int nacelle_temperature_index = 14;
	int nacelle_vibration_effective_value_index = 15;
	int nacelle_vibration_sensor_momentary_offset_max_index = 16;
	int nacelle_vibration_sensor_x_index = 17;
	int nacelle_vibration_sensor_y_index = 18;
	int pitch_drive_current_1_index = 19;
	int pitch_drive_current_2_index = 20;
	int pitch_drive_current_3_index = 21;
	int pitch_position_1_index = 22;
	int pitch_position_2_index = 23;
	int pitch_position_3_index = 24;
	int pitch_ssb_motor_current_1_index = 25;
	int pitch_ssb_motor_current_2_index = 26;
	int pitch_ssb_motor_current_3_index = 27;
	int pitch_ssb_motor_temperature_1_index = 28;
	int pitch_ssb_motor_temperature_2_index = 29;
	int pitch_ssb_motor_temperature_3_index = 30;
	int rotor_speed_index = 31;
	int wind_speed_index = 32;
	
}
