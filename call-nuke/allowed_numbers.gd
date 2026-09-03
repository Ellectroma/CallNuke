extends Control

var _plugin = null

@onready var _list_container: VBoxContainer = %ListContainer
@onready var _number_input: LineEdit = %NumberInput


func _ready() -> void:
	if Engine.has_singleton("CallNuke"):
		_plugin = Engine.get_singleton("CallNuke")
	_refresh_list()


func _refresh_list() -> void:
	for child in _list_container.get_children():
		child.queue_free()

	var numbers: Array = []
	if _plugin:
		var json := JSON.new()
		var result := json.parse(_plugin.getAllowedNumbers())
		if result == OK and json.get_data() is Array:
			numbers = json.get_data()

	for number in numbers:
		_add_row(str(number))


func _add_row(number: String) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)

	var label := Label.new()
	label.text = number
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(label)

	var remove_button := Button.new()
	remove_button.text = "X"
	remove_button.pressed.connect(_on_remove_pressed.bind(number))
	row.add_child(remove_button)

	_list_container.add_child(row)


func _on_remove_pressed(number: String) -> void:
	if _plugin:
		_plugin.removeAllowedNumber(number)
	_refresh_list()


func _on_add_pressed() -> void:
	_add_number()


func _on_number_submitted(_text: String) -> void:
	_add_number()


func _add_number() -> void:
	var number := _number_input.text.strip_edges()
	if number.is_empty():
		return
	if _plugin:
		_plugin.addAllowedNumber(number)
	_number_input.text = ""
	_refresh_list()


func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://main.tscn")
