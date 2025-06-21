#
# IGinX - the polystore system with high performance
# Copyright (C) Tsinghua University
# TSIGinX@gmail.com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

import joblib
import numpy as np
import warnings
from UDFMLPredicate import UDFMLPredicate


class DTPriceCategory(UDFMLPredicate):
    def __init__(self):
        self.model = None
        self.scaler = None
        self.mean_ = None
        self.scale_ = None

    def get_scaler(self):
        return None

    def get_model(self):
        model_path = "/home/xyh/code/model/tpch/model/decision_tree_price_category.pkl"
        try:
            with open(model_path, 'rb') as file:
                self.model = joblib.load(file)
                return self.model
        except Exception as e:
            raise RuntimeError(f"Failed to load model from {model_path}: {e}")

    def transform(self, data, *args, **kwargs):
        res = self.buildHeader(data)
        warnings.filterwarnings("ignore")

        if self.model is None:
            self.get_model()
        if self.scaler is None:
            self.get_scaler()

        try:
            prediction = self.model.predict(np.array([data[2][1:]]))
            res.append([str(prediction[0])])
        except Exception as e:
            raise RuntimeError(f"Error during decision tree prediction: {e}")

        return res

    def buildHeader(self, data):
        return [["udf_dt(price_category)"], ["BINARY"]]
