package ch.edizeqiri.bevyecs

data class MessageStruct(
    val name: String,
    val fields: List<MessageField>
)

data class MessageField(
    val name: String,
    val type: String
)
