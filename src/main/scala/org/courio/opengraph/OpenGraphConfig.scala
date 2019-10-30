package org.courio.opengraph

import java.io.File

case class OpenGraphConfig(directory: File = new File(System.getProperty("java.io.tmpdir")),
                           previewMaxWidth: Int = 150,
                           previewMaxHeight: Int = 100,
                           generatePreview: Boolean = true)
