package com.musify.routes

import com.musify.presentation.controller.OfflineDownloadController
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.offlineDownloadRoutes() {
    val controller = OfflineDownloadController(application)
    
    authenticate("auth-jwt") {
        route("/api/offline") {
            // Download management
            post("/download") {
                controller.requestDownload(call)
            }
            
            get("/storage-info") {
                controller.getStorageInfo(call)
            }
            
            get("/download/{downloadId}/progress") {
                controller.getDownloadProgress(call)
            }
            
            delete("/download/{downloadId}/cancel") {
                controller.cancelDownload(call)
            }
            
            delete("/download/{downloadId}") {
                controller.deleteDownload(call)
            }
            
            // Smart downloads
            route("/smart-downloads") {
                post("/trigger") {
                    controller.triggerSmartDownloads(call)
                }
                
                get("/preferences") {
                    controller.getSmartDownloadPreferences(call)
                }
                
                put("/preferences") {
                    controller.updateSmartDownloadPreferences(call)
                }
                
                post("/record-play") {
                    controller.recordDownloadPlay(call)
                }
                
                get("/metrics") {
                    controller.getPredictionMetrics(call)
                }
            }
        }
    }
}