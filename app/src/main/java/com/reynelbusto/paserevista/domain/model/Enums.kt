package com.reynelbusto.paserevista.domain.model

/** Vocabularios cerrados del dominio. El `code` es lo que se persiste (TEXT). */

enum class Sex(val code: String) { M("M"), F("F") }

enum class PatientState(val code: String) {
    ACTIVE("active"),
    DISCHARGED("discharged"),
    TRANSFERRED("transferred"),
}

enum class JourneyState(val code: String) {
    FUTURE("future"),
    CURRENT("current"),
    COMPLETED("completed"),
}

enum class JourneyOrigin(val code: String) {
    AUTO("auto"),
    MANUAL("manual"),
}

/** ○ Pendiente · ◐ En curso · ✓ Revisado. Abrir ≠ revisar. */
enum class ReviewState(val code: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
}

enum class ClinicalEvolution(val code: String) {
    UNCHANGED("unchanged"),
    IMPROVED("improved"),
    WORSENED("worsened"),
    NEW_PROBLEM("new_problem"),
}

enum class ClinicalState(val code: String) {
    STABLE("stable"),
    WATCH("watch"),
    CRITICAL("critical"),
}

enum class ClinicalPriority(val code: String) {
    P1("p1"),
    P2("p2"),
    P3("p3"),
}

enum class PendingType(val code: String) {
    GENERAL("general"),
    STUDY("study"),
    PROCEDURE("procedure"),
    INTERCONSULTATION("interconsultation"),
}

enum class PendingState(val code: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELED("canceled"),
}

enum class TreatmentState(val code: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    FINALIZED("finalized"),
}

enum class TreatmentEventType(val code: String) {
    STARTED("started"),
    DOSE_CHANGED("dose_changed"),
    ROUTE_CHANGED("route_changed"),
    FREQUENCY_CHANGED("frequency_changed"),
    DRUG_CHANGED("drug_changed"),
    SUSPENDED("suspended"),
    RESUMED("resumed"),
    FINALIZED("finalized"),
}

enum class DeviceKind(val code: String) {
    FOLEY_CATHETER("sonda_vesical"),
    JJ_STENT("sonda_jj"),
    DRAIN("drenaje"),
    CATHETER("cateter"),
    STENT("stent"),
    OTHER("otro"),
}

enum class DeviceState(val code: String) {
    ACTIVE("active"),
    REMOVED("removed"),
}

enum class ResultType(val code: String) {
    LAB("lab"),
    IMAGING("imaging"),
    ANATOMY("anatomy"),
    MICRO("micro"),
    OTHER("other"),
}

enum class ProcedureState(val code: String) {
    PENDING("pending"),
    PREPARATION("preparation"),
    PERFORMED("performed"),
    CANCELED("canceled"),
}
