extends Control

var _plugin = null

@onready var _list_container: VBoxContainer = %ListContainer
@onready var _keyword_input: LineEdit = %KeywordInput


func _ready() -> void:
	if Engine.has_singleton("CallNuke"):
		_plugin = Engine.get_singleton("CallNuke")
	_refresh_list()


func _refresh_list() -> void:
	for child in _list_container.get_children():
		child.queue_free()

	var keywords: Array = []
	if _plugin:
		var json := JSON.new()
		var result := json.parse(_plugin.getTrustedKeywords())
		if result == OK and json.get_data() is Array:
			keywords = json.get_data()

	for keyword in keywords:
		_add_row(str(keyword))


func _add_row(keyword: String) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)

	var label := Label.new()
	label.text = keyword
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(label)

	var remove_button := Button.new()
	remove_button.text = "X"
	remove_button.pressed.connect(_on_remove_pressed.bind(keyword))
	row.add_child(remove_button)

	_list_container.add_child(row)


func _on_remove_pressed(keyword: String) -> void:
	if _plugin:
		_plugin.removeTrustedKeyword(keyword)
	_refresh_list()


func _on_add_pressed() -> void:
	_add_keyword()


func _on_keyword_submitted(_text: String) -> void:
	_add_keyword()


func _add_keyword() -> void:
	var keyword := _keyword_input.text.strip_edges()
	if keyword.is_empty():
		return
	if _plugin:
		_plugin.addTrustedKeyword(keyword)
	_keyword_input.text = ""
	_refresh_list()


func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://main.tscn")
