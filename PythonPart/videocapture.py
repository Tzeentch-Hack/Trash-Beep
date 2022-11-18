import requests
import cv2
import numpy as np
import PIL
import imutils
from detector import LitterDetector

url = "http://172.20.10.14:5001/"

# resp = requests.get(url + "beep")
ld = LitterDetector(0.7, 1280)

while True:
    img_resp = requests.get(url)
    img_arr = np.array(bytearray(img_resp.content), dtype=np.uint8)
    img = cv2.imdecode(img_arr, -1)
    # img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    img = imutils.resize(img, width=1200, height=640)
    det_img, beep = ld(img)

    #det_img = cv2.imdecode(det_img, -1)
    # cv2.namedWindow('image', cv2.WND_PROP_FULLSCREEN)
    # cv2.setWindowProperty('image', cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_FULLSCREEN)
    cv2.imshow("Android_cam", det_img)
    if beep:
        requests.get(url + "beep")
    # Press Esc key to exit
    if cv2.waitKey(1) == 27:
        break

cv2.destroyAllWindows()