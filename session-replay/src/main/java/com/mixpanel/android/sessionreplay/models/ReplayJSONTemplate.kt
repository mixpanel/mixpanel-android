package com.mixpanel.android.sessionreplay.models

object ReplayJSONTemplate {
    fun mainEventJSON(
        imageBase64: String,
        timestamp: Long
    ): String {
        return """
            {
                "type": 2,
                "data": {
                    "discriminator": "node",
                    "node": {
                        "type": 0,
                        "childNodes": [
                            {
                                "type": 1,
                                "name": "html",
                                "publicId": "",
                                "systemId": "",
                                "id": 2
                            },
                            {
                                "type": 2,
                                "tagName": "html",
                                "childNodes": [
                                    {
                                        "type": 2,
                                        "tagName": "head",
                                        "attributes": {},
                                        "childNodes": [
                                            {
                                                "type": 2,
                                                "tagName": "style",
                                                "attributes": {},
                                                "childNodes": [
                                                    {
                                                        "type": 3,
                                                        "textContent": "body { margin: 0px; padding: 0px; }.screen img { width: 100%; height: auto; display: block; }",
                                                        "isStyle": true,
                                                        "id": 18
                                                    }
                                                ],
                                                "id": 17
                                            }
                                        ],
                                        "id": 4
                                    },
                                   {
                                      "type":2,
                                      "tagName":"body",
                                      "attributes":{

                                      },
                                      "childNodes":[
                                         {
                                            "type":2,
                                            "tagName":"div",
                                            "attributes":{
                                               "class":"screen"
                                            },
                                            "childNodes":[
                                               {
                                                  "type":2,
                                                  "tagName":"img",
                                                  "attributes":{
                                                     "src":"data:image/jpeg;base64,$imageBase64"
                                                  },
                                                  "childNodes":[

                                                  ],
                                                  "id":28
                                               }
                                            ],
                                            "id":29
                                         }
                                      ],
                                      "id":25
                                   }
                                ],
                                "id": 3
                            }
                        ],
                        "id": 1
                    }
                },
                "timestamp": $timestamp
            }
        """.trimIndent()
        // Remove extra indentation from the raw string
    }
}
