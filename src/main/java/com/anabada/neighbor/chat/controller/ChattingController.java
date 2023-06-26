package com.anabada.neighbor.chat.controller;

import com.anabada.neighbor.chat.domain.Chat;
import com.anabada.neighbor.chat.domain.ChattingRoom;
import com.anabada.neighbor.chat.service.ChattingService;
import com.anabada.neighbor.config.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChattingController {

    private final ChattingService chattingService;

    @PostMapping("/openRoom")
    public String openRoom(ChattingRoom chattingRoom, Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        ChattingRoom result = chattingService.openRoom(chattingRoom, principalDetails);
        return "redirect:/chatDetail?roomId="+result.getRoomId();
    }

    @GetMapping("/chatRoomList")
    public String chatRoomList(Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        model.addAttribute("list", chattingService.chattingRoomList(principalDetails));
        return "chatEx/chatRoomListPopup";
    }

    @GetMapping("/chatDetail")
    public String chatDetail(long roomId, Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        model.addAttribute("list", chattingService.chattingMessageList(roomId, principalDetails));
        model.addAttribute("receiver", chattingService.getReceiver(roomId, principalDetails));
        model.addAttribute("roomId", roomId);
        return "chatEx/chatDetailPopup";
    }

    @MessageMapping("/message")
    public void messageRoom(Chat chat, Principal principal) throws InterruptedException {
        Thread.sleep(1000);
        chattingService.sendMessage(chat, principal);
    }

}
