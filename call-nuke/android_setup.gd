extends Control

var _plugin = null

@onready var _role_status: Label = %RoleStatus
@onready var _contacts_status: Label = %ContactsStatus
@onready var _sms_status: Label = %SmsStatus

const COLOR_GRANTED := Color(0.4, 0.85, 0.5, 1)
const COLOR_NOT_SET := Color(0.6470588, 0.6745098, 0.7137255, 1)


func _ready() -> void:
	if Engine.has_singleton("CallNuke"):
		_plugin = Engine.get_singleton("CallNuke")
	_refresh_status()


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_IN:
		_refresh_status()


func _refresh_status() -> void:
	if _plugin:
		_set_status(_role_status, _plugin.hasCallScreeningRole())
		_set_status(_contacts_status, _plugin.hasContactsPermission())
		_set_status(_sms_status, _plugin.hasSmsPermission())
	else:
		_set_status(_role_status, false)
		_set_status(_contacts_status, false)
		_set_status(_sms_status, false)


func _set_status(label: Label, granted: bool) -> void:
	if granted:
		label.text = "Status: ● Granted"
		label.add_theme_color_override("font_color", COLOR_GRANTED)
	else:
		label.text = "Status: ○ Not set"
		label.add_theme_color_override("font_color", COLOR_NOT_SET)


func _on_role_button_pressed() -> void:
	if _plugin:
		_plugin.requestCallScreeningRole()


func _on_contacts_button_pressed() -> void:
	if _plugin:
		_plugin.requestContactsPermission()


func _on_sms_button_pressed() -> void:
	if _plugin:
		_plugin.requestSmsPermission()


func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://main.tscn")
