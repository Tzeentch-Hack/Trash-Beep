import torch
import time


class LitterDetector(object):
    def __init__(self, conf, size):
        self.model = torch.hub.load('ultralytics/yolov5', 'custom', path='taco2_epoch300_best.pt')
        # self.model.nc =
        # self.model.names = ['Clear plastic bottle', 'Plastic bottle cap', 'Drink can',
        # 'Other plastic', 'Plastic film', 'Other plastic wrapper','Unlabeled litter', 'Cigarette']
        self.model.names = ['Trash', 'Trash']
        # self.model.names = ['Trash']

        self.model.conf = conf
        self.model.multi_label = True
        self.size = size
        self.temp_results = self.model('1.jpg', size=self.size)
        self.model.iou = 0.6
        self.model.amp = True
        self.prev_len = 0

    def __call__(self, image_path):
        tmp_results = self.model(image_path, size=self.size)
        beep = False
        cur_len = len(tmp_results.xyxy[0])
        if cur_len > self.prev_len:
            beep = True
        self.prev_len = cur_len
        return tmp_results.render()[0], beep
