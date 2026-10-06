package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import kotlinx.coroutines.flow.Flow

class GetDailyRecordsUseCase(private val records: DailyRecordRepository) {
    operator fun invoke(journeyId: String): Flow<List<DailyRecord>> =
        records.observeByJourney(journeyId)
}
