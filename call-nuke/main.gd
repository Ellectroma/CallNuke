extends Control

var _plugin = null

@onready var _call_toggle: CheckButton = %CallToggle
@onready var _sms_toggle: CheckButton = %SmsToggle
@onready var _calls_count_value: Label = %CallsCountValue
@onready var _sms_count_value: Label = %SmsCountValue


func _ready() -> void:
	if Engine.has_singleton("CallNuke"):
		_plugin = Engine.get_singleton("CallNuke")
	_refresh_state()


func _refresh_state() -> void:
	if _plugin:
		_call_toggle.set_pressed_no_signal(_plugin.isCallProtectionEnabled())
		_sms_toggle.set_pressed_no_signal(_plugin.isSmsProtectionEnabled())
		_calls_count_value.text = str(_plugin.getBlockedCallsCount())
		_sms_count_value.text = str(_plugin.getFilteredSmsCount())
	else:
		_calls_count_value.text = "0"
		_sms_count_value.text = "0"


func _on_call_toggle_toggled(button_pressed: bool) -> void:
	if _plugin:
		_plugin.setCallProtectionEnabled(button_pressed)


func _on_sms_toggle_toggled(button_pressed: bool) -> void:
	if _plugin:
		_plugin.setSmsProtectionEnabled(button_pressed)


func _on_allowed_numbers_pressed() -> void:
	get_tree().change_scene_to_file("res://allowed_numbers.tscn")


func _on_sms_keywords_pressed() -> void:
	get_tree().change_scene_to_file("res://sms_keywords.tscn")


func _on_android_setup_pressed() -> void:
	get_tree().change_scene_to_file("res://android_setup.tscn")


func _on_refresh_timer_timeout() -> void:
	if _plugin:
		_calls_count_value.text = str(_plugin.getBlockedCallsCount())
		_sms_count_value.text = str(_plugin.getFilteredSmsCount())
