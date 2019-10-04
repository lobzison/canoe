package canoe.models

/**
  * Version of a photo or a file / sticker thumbnail of specifi1c size.
  */
case class PhotoSize(fileId: String, width: Int, height: Int, fileSize: Option[Int])
