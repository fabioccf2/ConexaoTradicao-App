package com.conexaotradicao.app.data.local

import androidx.room.TypeConverter
import com.conexaotradicao.app.data.model.Animal
import com.conexaotradicao.app.data.model.ParticipationStatus
import com.conexaotradicao.app.data.model.UserRole

/** Conversores para o Room persistir enums e listas simples. */
class Converters {

    @TypeConverter
    fun fromAnimal(value: Animal): String = value.name

    @TypeConverter
    fun toAnimal(value: String): Animal = Animal.valueOf(value)

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromParticipationStatus(value: ParticipationStatus): String = value.name

    @TypeConverter
    fun toParticipationStatus(value: String): ParticipationStatus = ParticipationStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
