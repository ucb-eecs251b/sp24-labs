# template_sky130.tcl : template Tcl file

set_var ski_enable 0
set_units -capacitance 1pf -leakage_power 1nW -timing 1ns

set_var slew_lower_rise 0.1
set_var slew_lower_fall 0.1
set_var slew_upper_rise 0.9
set_var slew_upper_fall 0.9

set_var measure_slew_lower_rise 0.1
set_var measure_slew_lower_fall 0.1
set_var measure_slew_upper_rise 0.9
set_var measure_slew_upper_fall 0.9

set_var delay_inp_rise 0.5
set_var delay_inp_fall 0.5
set_var delay_out_rise 0.5
set_var delay_out_fall 0.5

set_var def_arc_msg_level 0
set_var process_match_pins_to_ports 1
set_var max_transition 2e-9
set_var min_transition 5e-12
set_var min_output_cap 1e-15


define_template -type delay \
         -index_1 {0.005 0.01 0.04 0.08 0.20 0.65 1.40 } \
         -index_2 {0.0010 0.00300 0.00850 0.01500 0.04000 0.10000 0.20000 } \
         delay_template_7x7

define_template -type constraint \
         -index_1 {0.01 0.025 0.05 0.10 0.30 0.8 1.40 } \
         -index_2 {0.01 0.025 0.05 0.10 0.30 0.8 1.40 } \
         constraint_template_7x7

define_template -type power \
         -index_1 {0.005 0.01 0.04 0.08 0.20 0.65 1.40 } \
         -index_2 {0.0010 0.00300 0.00850 0.01500 0.04000 0.10000 0.20000 } \
         power_template_7x7
