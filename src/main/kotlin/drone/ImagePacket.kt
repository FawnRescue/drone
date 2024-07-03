package drone

import supabase.domain.Image

data class ImagePacket(val images: ImagesData, val metadata: Image)
