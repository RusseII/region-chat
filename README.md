# Global World Chat

Ever tried to have a conversation with someone but they have been too far away and did not recieve this message?

This plugin is for you! This plugin ensures that your chat will reach one another, so long as you're in the same world.

World 491 is the homeworld for this plugin. You will be most likely to run into other plugin users on this world. 

## ⚠️ Service Status & Limits

**WARNING: Service will go offline when limits are reached!**

### Current Status and Limits:
- **Connection limit:** 200 concurrent users
- **Message limit:** 6 million per month
- **Channel limit:** 200 active channels

### Your support will:
- Increase connection limits (more players can chat)
- Increase message limits (no more outages)
- Keep the service running 24/7
- Enable new features and improvements

**[Support on Patreon](https://patreon.com/global_chat_plugin)** to help keep Global Chat running for everyone!

## How does this work?

This project makes use of [Ably](https://www.ably.com), a realtime messaging and data synchronization platform, in order to communicate between clients. [Channels](https://ably.com/channels) are used to divide up communication between clients. Each channel's name is structured so as to indicate the world, that the channel encapsulates. For example:

`globalchat:[world]`

When a player enters one of the worlds which support Global Chat, they'll automatically enter the channel for that world. They'll subscribe to messages from it, and any messages received will be shown in the chat, assuming the message wasn't already seen. Any messages you send in this world will also be sent to the Ably Channel, so others can see them.

Due to Ably fundamentally being a Pub/Sub system, any identifying information (such as IP) will not be made accessible to other clients. The only thing other clients can see is the content of your message.

This is an intentionally simple implementation of a 1-to-1 channel to region system without additional bells and whistles. It'd be possible to implement better authentication, division of regions, and more.

New features like filtering messages by names and other customization options will be coming soon.
