package no.nav.syfo.model

import java.time.LocalDateTime

data class ReceivedLegeerklaering(
    val legeerklaering: Legeerklaering,
    val personNrPasient: String,
    val pasientAktoerId: String,
    val personNrLege: String,
    val legeAktoerId: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    /**
     * Full fellesformat as a XML payload, this is only used for infotrygd compat and should be
     * removed in thefuture
     */
    val fellesformat: String,
    /** TSS-ident, this is only used for infotrygd compat and should be removed in thefuture */
    val tssid: String?,
    val conversationRef: ConversationRef?
)

data class ConversationRef(val refToParent: String?, val refToConversation: String?)
