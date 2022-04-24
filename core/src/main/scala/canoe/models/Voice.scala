package canoe.models

/**
  * Represents Telegram voice note
  */
final case class Voice(fileId: String,
                       fileUniqueId: String,
                       duration: Int,
                       mimeType: Option[String],
                       fileSize: Option[Int])
