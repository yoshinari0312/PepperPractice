import socket

pepper_ip = "192.168.1.50"  # PepperのIPアドレス
pepper_port = 2002  # Android アプリのポート


def send_message(message):
    """ Android アプリにメッセージを送信する関数 """
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client_socket:
            client_socket.connect((pepper_ip, pepper_port))
            print(f"接続成功: {pepper_ip}:{pepper_port}")

            # メッセージを送信
            client_socket.sendall(message.encode('utf-8') + b'\n')
            print(f"送信: {message}")

            # 応答を受信
            response = client_socket.recv(1024).decode('utf-8').strip()
            print(f"受信: {response}")

    except Exception as e:
        print(f"エラー: {e}")


if __name__ == "__main__":
    print("==== Pepper TCP クライアント ====")
    while True:
        print("\nメニュー:")
        print("1: Pepper に発話させる")
        # print("2: Pepper を移動させる")
        print("2: Pepper の視線を向ける")
        print("3: 終了")

        choice = input("選択 (1-3): ").strip()

        if choice == "1":
            text = input("発話する内容を入力: ").strip()
            send_message(f"say:{text}")

        # elif choice == "2":
        #     print("移動コマンドを送信（デフォルトでは x 方向に 1m 移動）")
        #     send_message("goto:1")

        elif choice == "2":
            print("視線の方向を選択:")
            print("1: 右（only head）")
            print("2: 左（only head）")
            print("3: 正面（only head）")
            print("4: 右（full body）")
            print("5: 左（full body）")
            print("6: 正面（full body）")

            look_choice = input("選択 (1-6): ").strip()
            
            if look_choice == "1":
                send_message("look:onlyHead:right")
            elif look_choice == "2":
                send_message("look:onlyHead:left")
            elif look_choice == "3":
                send_message("look:onlyHead:center")
            elif look_choice == "4":
                send_message("look:fullBody:right")
            elif look_choice == "5":
                send_message("look:fullBody:left")
            elif look_choice == "6":
                send_message("look:fullBody:center")
            else:
                print("無効な選択です。1-6 を入力してください。")

        elif choice == "3":
            print("プログラムを終了します。")
            break

        else:
            print("無効な選択です。1-3 を入力してください。")
