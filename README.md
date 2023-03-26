# Ably Region Chat

Ever tried to have a conversation with someone whilst Barbarian Fishing, only to have it cut short by an impromptu split due to the fishing spot moving? Separated at Tempoross due to being sent different ships?

This plugin is for you! This plugin ensures that your chat will reach one another, so long as you're in the same rough area. Currently this plugin will work for the following areas:

* Barbarian Fishing south of Barbarian Outpost
* The Tempoross Island
* Zeah Runecrafting area
* Motherlode mine
* Sepulchre
* Zalcano

## How does this work?

This project makes use of [Ably](https://www.ably.com), a realtime messaging and data synchronization platform, in order to communicate between clients. [Channels](https://ably.com/channels) are used to divide up communication between clients. Each channel's name is structured so as to indicate the region, world, and where relevant the instance ID that the channel encapsulates. For example:

`regionchat:[regionName]:[world]:[instanceID]`

When a player enters one of the regions which support Region Chat, they'll automatically enter the channel for that region for that world. They'll subscribe to messages from it, and any messages received will be shown in the chat, assuming the message wasn't already seen. Any messages you send in this region will also be sent to the Ably Channel, so others can see them.

Due to Ably fundamentally being a Pub/Sub system, any identifying information (such as IP) will not be made accessible to other clients. The only thing other clients can see is the content of your message.

This is an intentionally simple implementation of a 1-to-1 channel to region system without additional bells and whistles. It'd be possible to implement better authentication, division of regions, and more.